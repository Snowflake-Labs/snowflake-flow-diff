/*
 * Copyright 2025 Snowflake Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.snowflake.openflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig;
import com.snowflake.openflow.github.GitHubClient;
import org.apache.nifi.flow.Bundle;
import org.apache.nifi.flow.ComponentType;
import org.apache.nifi.flow.ConnectableComponent;
import org.apache.nifi.flow.VersionedComponent;
import org.apache.nifi.flow.VersionedConfigurableExtension;
import org.apache.nifi.flow.VersionedConnection;
import org.apache.nifi.flow.VersionedControllerService;
import org.apache.nifi.flow.VersionedFlowCoordinates;
import org.apache.nifi.flow.VersionedLabel;
import org.apache.nifi.flow.VersionedParameter;
import org.apache.nifi.flow.VersionedParameterContext;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.flow.VersionedPropertyDescriptor;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;
import org.apache.nifi.registry.flow.RegisteredFlowSnapshot;
import org.apache.nifi.registry.flow.diff.ConciseEvolvingDifferenceDescriptor;
import org.apache.nifi.registry.flow.diff.FlowComparator;
import org.apache.nifi.registry.flow.diff.FlowComparatorVersionedStrategy;
import org.apache.nifi.registry.flow.diff.FlowDifference;
import org.apache.nifi.registry.flow.diff.StandardComparableDataFlow;
import org.apache.nifi.registry.flow.diff.StandardFlowComparator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

public class FlowDiff {

    private static final int RETURN_SUCCESS = 0;
    private static final int RETURN_FAILURE = 1;
    private static final int RETURN_CHECKSTYLE_VIOLATIONS = 2;

    private static String flowName;
    private static Map<String, VersionedParameterContext> parameterContexts;
    private static Map<String, VersionedProcessGroup> processGroups;
    private static List<String> checkstyleViolations;

    public static void main(String[] args) throws IOException {
        final int exitCode = run(args);
        System.exit(exitCode);
    }

    static int run(String[] args) throws IOException {
        // Arguments match the order from action.yml:
        // args[0] = flowA
        // args[1] = flowB
        // args[2] = token
        // args[3] = repository
        // args[4] = issuenumber
        // args[5] = checkstyle
        // args[6] = checkstyle-rules
        // args[7] = checkstyle-fail

        final List<String> pathsA = List.of(args[0].split(",")).stream().map(String::trim).toList();
        final List<String> pathsB = List.of(args[1].split(",")).stream().map(String::trim).toList();

        // GitHub API parameters (optional - if not provided, output goes to stdout only)
        final String githubToken = args.length > 2 && args[2] != null && !args[2].isEmpty() ? args[2] : null;
        final String githubRepository = args.length > 3 && args[3] != null && !args[3].isEmpty() ? args[3] : null;
        final String githubIssueNumber = args.length > 4 && args[4] != null && !args[4].isEmpty() ? args[4] : null;

        final boolean checkstyleEnabled = args.length > 5 && args[5] != null && !args[5].isEmpty()
                ? Boolean.parseBoolean(args[5])
                : false;
        final CheckstyleRulesConfig rulesConfig = args.length > 6 && args[6] != null && !args[6].isEmpty()
                ? CheckstyleRulesConfig.fromFile(args[6])
                : null;
        final boolean failOnCheckstyleViolations = args.length > 7 && args[7] != null && !args[7].isEmpty()
                ? Boolean.parseBoolean(args[7])
                : false;

        // Capture output to a string if we need to post to GitHub
        final ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        final PrintStream originalOut = System.out;
        final PrintStream captureStream = new PrintStream(outputCapture, true, StandardCharsets.UTF_8);

        if (githubToken != null && githubRepository != null && githubIssueNumber != null) {
            System.setOut(captureStream);
        }

        try {
            System.out.println("> [!NOTE]");
            System.out.println("> This GitHub Action is created and maintained by [Snowflake](https://www.snowflake.com/).");
            System.out.println("");

            if (pathsA.size() != pathsB.size()) {
                System.out.println("The action didn't properly identify the files to compare. Please check the input files.");
                return RETURN_FAILURE;
            } else {
                System.out.println("Identified " + pathsA.size() + " changed flows in this Pull Request.");
            }

            boolean hasBlockingCheckstyleViolations = false;

            for (int i = 0; i < pathsA.size(); i++) {
                System.out.println("");

                flowName = "";
                parameterContexts = new HashMap<>();
                processGroups = new HashMap<>();

                final boolean flowHasCheckstyleViolations = executeFlowDiffForOneFlow(pathsA.get(i), pathsB.get(i), checkstyleEnabled, rulesConfig);
                hasBlockingCheckstyleViolations = hasBlockingCheckstyleViolations || flowHasCheckstyleViolations;
            }

            // Post to GitHub if credentials are provided
            if (githubToken != null && githubRepository != null && githubIssueNumber != null) {
                System.setOut(originalOut);
                final String output = outputCapture.toString(StandardCharsets.UTF_8);

                // Also print to stdout for logging
                System.out.println(output);

                // Post the new comment first, then delete old ones (safer: if posting fails, old comments remain)
                final GitHubClient gitHubClient = new GitHubClient(githubToken, githubRepository, githubIssueNumber);
                final boolean postSuccess = gitHubClient.postComment(output);
                if (postSuccess) {
                    gitHubClient.deletePreviousComments();
                }
            }

            if (checkstyleEnabled && failOnCheckstyleViolations && hasBlockingCheckstyleViolations) {
                return RETURN_CHECKSTYLE_VIOLATIONS;
            }

            return RETURN_SUCCESS;
        } finally {
            System.setOut(originalOut);
        }
    }

    private static boolean executeFlowDiffForOneFlow(final String pathA, final String pathB,
            final boolean checkstyleEnabled, final CheckstyleRulesConfig rulesConfig) throws IOException {
        final Set<FlowDifference> diffs = getDiff(pathA, pathB, checkstyleEnabled, rulesConfig);
        final Set<String> bundleChanges = new HashSet<>();
        boolean flowHasCheckstyleViolations = false;

        System.out.println("### Executing Snowflake Flow Diff for flow: " + flowName);

        if (checkstyleEnabled && checkstyleViolations != null && !checkstyleViolations.isEmpty()) {
            System.out.println("#### Checkstyle Violations");
            System.out.println("> [!CAUTION]");
            for (String violation : checkstyleViolations) {
                System.out.println("> - " + violation);
            }
            System.out.println("");
            flowHasCheckstyleViolations = true;
        } else if (checkstyleEnabled && (checkstyleViolations == null || checkstyleViolations.isEmpty())) {
            System.out.println("#### No Checkstyle Violations found");
        }

        if (diffs != null && !diffs.isEmpty()) {

            System.out.println("#### Flow Changes");

            for (FlowDifference diff : diffs) {

                switch (diff.getDifferenceType()) {
                case COMPONENT_ADDED: {
                    if (diff.getComponentB().getComponentType().equals(ComponentType.FUNNEL)) {
                        System.out.println("- A Funnel has been added");
                    } else if (diff.getComponentB().getComponentType().equals(ComponentType.CONNECTION)) {
                        final VersionedConnection connection = (VersionedConnection) diff.getComponentB();
                        printConnection(connection);
                    } else if (diff.getComponentB().getComponentType().equals(ComponentType.PROCESSOR)) {
                        final VersionedProcessor proc = (VersionedProcessor) diff.getComponentB();
                        System.out.println("- A " + printComponent(diff.getComponentB())
                                + " has been added with the configuration [" + printProcessorConf(proc) + "] and the below properties:");
                        printConfigurableExtensionProperties(proc);
                    } else if (diff.getComponentB().getComponentType().equals(ComponentType.CONTROLLER_SERVICE)) {
                        final VersionedControllerService cs = (VersionedControllerService) diff.getComponentB();
                        final String pgName = processGroups.get(cs.getGroupIdentifier()).getName();
                        System.out.println("- A " + printComponent(diff.getComponentB())
                                + " has been added in Process Group `" + pgName + "` with the below properties:");
                        printConfigurableExtensionProperties(cs);
                    } else if (diff.getComponentB().getComponentType().equals(ComponentType.LABEL)) {
                        final VersionedLabel label = (VersionedLabel) diff.getComponentB();
                        System.out.println("- A Label has been added with the below text:");
                        System.out.println("```");
                        System.out.println(label.getLabel());
                        System.out.println("```");
                    } else {
                        System.out.println("- A " + diff.getComponentB().getComponentType().getTypeName()
                                + (isEmpty(diff.getComponentB().getName()) ? "" : " named `" + diff.getComponentB().getName() + "`")
                                + " has been added");
                    }
                    break;
                }
                case COMPONENT_REMOVED: {
                    if (diff.getComponentA().getComponentType().equals(ComponentType.FUNNEL)) {
                        System.out.println("- A Funnel has been removed");
                    } else if (diff.getComponentA().getComponentType().equals(ComponentType.CONNECTION)) {
                        final VersionedConnection connection = (VersionedConnection) diff.getComponentA();
                        if (connection.getSource().getId().equals(connection.getDestination().getId())) {
                            System.out.println("- A self-loop connection `"
                                    + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                                    + "` has been removed from `" + connection.getSource().getName() + "`");
                        } else {
                            System.out.println("- A connection `"
                                    + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                                    + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                                    + "` has been removed");
                        }
                    } else {
                        System.out.println("- A " + printComponent(diff.getComponentA()) + " has been removed");
                    }
                    break;
                }
                case DESTINATION_CHANGED: {
                    System.out.println("- The destination of a connection has changed from `" + ((ConnectableComponent) diff.getValueA()).getName()
                            + "` to `" + ((ConnectableComponent) diff.getValueB()).getName() + "`");
                    break;
                }
                case PROPERTY_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA()) + ", the value of the property "
                            + "`" + diff.getFieldName().get() + "` changed from " + printFromTo(diff.getValueA().toString(), diff.getValueB().toString()));
                    break;
                }
                case CONCURRENT_TASKS_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA()) + ", the number of concurrent tasks has been "
                            + ((int) diff.getValueA() > (int) diff.getValueB() ? "decreased" : "increased")
                            + " from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case BACKPRESSURE_DATA_SIZE_THRESHOLD_CHANGED: {
                    final VersionedConnection connection = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The data size backpressure threshold for the connection `"
                            + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                            + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                            + "` has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case BACKPRESSURE_OBJECT_THRESHOLD_CHANGED: {
                    final VersionedConnection connection = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The flowfile number backpressure threshold for the connection `"
                            + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                            + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                            + "` has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case BULLETIN_LEVEL_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the bulletin level has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case RUN_DURATION_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the Run Duration changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case RUN_SCHEDULE_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the Run Schedule changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case AUTO_TERMINATED_RELATIONSHIPS_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the list of auto-terminated relationships changed from "
                            + "`" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case LOAD_BALANCE_STRATEGY_CHANGED: {
                    final VersionedConnection connection = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The load balancing strategy for the connection `"
                            + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                            + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                            + "` has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case LOAD_BALANCE_COMPRESSION_CHANGED: {
                    final VersionedConnection connection = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The load balancing compression for the connection `"
                            + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                            + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                            + "` has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case FLOWFILE_EXPIRATION_CHANGED: {
                    final VersionedConnection connection = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The flow file expiration for the connection `"
                            + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                            + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                            + "` has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case PENALTY_DURATION_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the penalty duration changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case PARAMETER_CONTEXT_CHANGED: {
                    final VersionedProcessGroup pg = (VersionedProcessGroup) diff.getComponentB();
                    System.out.println("- The Parameter Context `" + pg.getParameterContextName() + "` with parameters `"
                            + printParameterContext(parameterContexts.get(pg.getParameterContextName()))
                            + "` has been added to the process group `" + pg.getName() + "`");
                    break;
                }
                case SCHEDULING_STRATEGY_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the Scheduling Strategy changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case BUNDLE_CHANGED:
                    Bundle before = (Bundle) diff.getValueA();
                    Bundle after = (Bundle) diff.getValueB();
                    bundleChanges.add("- The bundle `"
                            + before.getGroup() + ":" + before.getArtifact()
                            + "` has been changed from version "
                            + "`" + before.getVersion() + "` to version `" + after.getVersion() + "`");
                    break;
                case NAME_CHANGED: {
                    System.out.println("- A " + printComponent(diff.getComponentA())
                            + " has been renamed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case PROPERTY_ADDED: {
                    final String propKey = diff.getFieldName().get();
                    String propValue = null;
                    if (diff.getComponentB() instanceof VersionedConfigurableExtension) {
                        final VersionedPropertyDescriptor propertyDescriptor = ((VersionedConfigurableExtension) diff.getComponentB()).getPropertyDescriptors().get(propKey);
                        if (propertyDescriptor != null && propertyDescriptor.isSensitive()) {
                            propValue = "<Sensitive Value>";
                        } else {
                            propValue = ((VersionedConfigurableExtension) diff.getComponentB()).getProperties().get(propKey);
                        }
                    }
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", a property has been added: " + "`" + propKey + "` = `" + propValue + "`");
                    break;
                }
                case PROPERTY_PARAMETERIZED: {
                    final String propKey = diff.getFieldName().get();
                    String propValue = null;
                    if (diff.getComponentB() instanceof VersionedProcessor) {
                        propValue = ((VersionedProcessor) diff.getComponentB()).getProperties().get(propKey);
                    }
                    if (diff.getComponentB() instanceof VersionedControllerService) {
                        propValue = ((VersionedControllerService) diff.getComponentB()).getProperties().get(propKey);
                    }
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", a property is now referencing a parameter: "
                            + "`" + propKey + "` = `" + propValue + "`");
                    break;
                }
                case PROPERTY_PARAMETERIZATION_REMOVED: {
                    final String propKey = diff.getFieldName().get();
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the property `" + propKey + "` is no longer referencing a parameter");
                    break;
                }
                case SCHEDULED_STATE_CHANGED: {
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the Schedule State changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                }
                case PARAMETER_ADDED: {
                    final String paramKey = diff.getFieldName().get();
                    final VersionedParameterContext pc = (VersionedParameterContext) diff.getComponentB();
                    final VersionedParameter param = pc.getParameters().stream().filter(p -> p.getName().equals(paramKey)).findFirst().get();

                    final String description;
                    if (isEmpty(param.getDescription())) {
                        description = "";
                    } else if (isMultiline(param.getDescription())) {
                        description = " with the description\n```\n" + param.getDescription() + "\n```";
                    } else {
                        description = " with the description `" + param.getDescription() + "`";
                    }

                    System.out.println("- In the Parameter Context `" + pc.getName() + "` a parameter has been added: `"
                            + paramKey + "` = `" + (param.isSensitive() ? "<Sensitive Value>" : param.getValue()) + "`"
                            + description);
                    break;
                }
                case PARAMETER_REMOVED: {
                    System.out.println("- In the Parameter Context `" + diff.getComponentB().getName()
                            + "` the parameter `" + diff.getFieldName().get() + "` has been removed");
                    break;
                }
                case PROPERTY_REMOVED: {
                    System.out.println("- In " + printComponent(diff.getComponentA()) + ", the property `" + diff.getFieldName().get() + "` has been removed");
                    break;
                }
                case PARAMETER_VALUE_CHANGED: {
                    final String paramKey = diff.getFieldName().get();
                    final VersionedParameterContext pcBefore = (VersionedParameterContext) diff.getComponentA();
                    final VersionedParameterContext pcAfter = (VersionedParameterContext) diff.getComponentB();
                    final VersionedParameter paramBefore = pcBefore.getParameters().stream().filter(p -> p.getName().equals(paramKey)).findFirst().get();
                    final VersionedParameter paramAfter = pcAfter.getParameters().stream().filter(p -> p.getName().equals(paramKey)).findFirst().get();
                    System.out.println("- In the Parameter Context `" + pcAfter.getName()
                            + "`, the value of the parameter `" + paramKey + "` has changed from "
                            + printFromTo(paramBefore.isSensitive() ? "<Sensitive Value>" : paramBefore.getValue(),
                                    paramAfter.isSensitive() ? "<Sensitive Value>" : paramAfter.getValue()));
                    break;
                }
                case INHERITED_CONTEXTS_CHANGED:
                    final VersionedParameterContext pc = (VersionedParameterContext) diff.getComponentA();
                    System.out.println("- In the Parameter Context `" + pc.getName()
                            + "`, the list of inherited parameter contexts changed from `"
                            + diff.getValueA() + "`" + " to `" + diff.getValueB() + "`");
                    break;
                case PARTITIONING_ATTRIBUTE_CHANGED:
                    final VersionedConnection pacConnection = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The partitioning attribute for the connection `"
                            + (isEmpty(pacConnection.getName()) ? pacConnection.getSelectedRelationships().toString() : pacConnection.getName())
                            + "` from `" + pacConnection.getSource().getName() + "` to `" + pacConnection.getDestination().getName()
                            + "` has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case PARAMETER_DESCRIPTION_CHANGED:
                    final String paramKey = diff.getFieldName().get();
                    final VersionedParameterContext pdcPc = (VersionedParameterContext) diff.getComponentA();
                    System.out.println("- In the Parameter Context `" + pdcPc.getName() + "` the description of the parameter `"
                            + paramKey + "` has changed from " + printFromTo(diff.getValueA().toString(), diff.getValueB().toString()));
                    break;
                case PRIORITIZERS_CHANGED:
                    final VersionedConnection connectionPrio = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The prioritizers for the connection `"
                            + (isEmpty(connectionPrio.getName()) ? connectionPrio.getSelectedRelationships().toString() : connectionPrio.getName())
                            + "` from `" + connectionPrio.getSource().getName() + "` to `" + connectionPrio.getDestination().getName()
                            + "` changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case SELECTED_RELATIONSHIPS_CHANGED:
                    final VersionedConnection connectionSRC = (VersionedConnection) diff.getComponentA();
                    System.out.println("- The selected relationships for the connection `"
                            + (isEmpty(connectionSRC.getName()) ? connectionSRC.getSelectedRelationships().toString() : connectionSRC.getName())
                            + "` from `" + connectionSRC.getSource().getName() + "` to `" + connectionSRC.getDestination().getName()
                            + "` has been changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case YIELD_DURATION_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the yield duration changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case RETRY_COUNT_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the Number of Retry Attempts changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case MAX_BACKOFF_PERIOD_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the Retry Maximum Back Off Period changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case BACKOFF_MECHANISM_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the Retry Back Off Policy changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case COMMENTS_CHANGED:
                    System.out.println("- The comment for the " + printComponent(diff.getComponentA())
                            + " has been changed from " + printFromTo(diff.getValueA().toString(), diff.getValueB().toString()));
                    break;
                case RETRIED_RELATIONSHIPS_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentA())
                            + ", the list of retried relationships changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case LABEL_VALUE_CHANGED:
                    System.out.println("- A label has been updated and its text has been changed from "
                            + printFromTo(diff.getValueA().toString(), diff.getValueB().toString()));
                    break;
                case EXECUTION_MODE_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentB())
                            + ", the Execution Mode changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case PROPERTY_SENSITIVITY_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentA()) + ", the sensitivity of the property `"
                            + diff.getFieldName().get() + "` changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case SIZE_CHANGED, STYLE_CHANGED, POSITION_CHANGED, BENDPOINTS_CHANGED, ZINDEX_CHANGED:
                    // no need to print these, they are not relevant for the user
                    break;
                case FLOWFILE_CONCURRENCY_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentB())
                            + ", the FlowFile Concurrency changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case FLOWFILE_OUTBOUND_POLICY_CHANGED:
                    System.out.println("- In " + printComponent(diff.getComponentB())
                            + ", the FlowFile Outbound Policy changed from `" + diff.getValueA() + "` to `" + diff.getValueB() + "`");
                    break;
                case VERSIONED_FLOW_COORDINATES_CHANGED:
                    final VersionedProcessGroup pg = (VersionedProcessGroup) diff.getComponentA();
                    final VersionedFlowCoordinates vfcBefore = (VersionedFlowCoordinates) diff.getValueA();
                    final VersionedFlowCoordinates vfcAfter = (VersionedFlowCoordinates) diff.getValueB();
                    System.out.println("- The Versioned Flow Coordinates for the Process Group `" + pg.getName() + "` have changed: "
                            + printVFCChanges(vfcBefore, vfcAfter));
                    break;

                default:
                    System.out.println("- " + diff.getDescription() + " (" + diff.getDifferenceType() + ")");
                    System.out.println("  - " + diff.getValueA());
                    System.out.println("  - " + diff.getValueB());
                    System.out.println("  - " + diff.getComponentA());
                    System.out.println("  - " + diff.getComponentB());
                    System.out.println("  - " + diff.getFieldName());
                    break;
                }
            }

            if (bundleChanges.size() > 0) {
                System.out.println("");
                System.out.println("#### Bundle Changes");
                for (String bundleChange : bundleChanges) {
                    System.out.println(bundleChange);
                }
            }
        } else if (diffs == null) {
            System.out.println("#### No changes as this is the first version of the flow");
        } else {
            System.out.println("#### No relevant changes found in the flow");
        }

        return flowHasCheckstyleViolations;
    }

    public static Set<FlowDifference> getDiff(final String pathA, final String pathB,
            final boolean checkstyleEnabled, final CheckstyleRulesConfig rulesConfig) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        final JsonFactory factory = new JsonFactory(objectMapper);
        boolean noOriginalFlow = false;

        FlowSnapshotContainer snapshotA = null;
        try {
            snapshotA = getFlowContainer(pathA, factory);
        } catch (Exception e) {
            // no original flow - meaning that the Github Action is executed against the
            // first version of the flow
            noOriginalFlow = true;
        }
        final FlowSnapshotContainer snapshotB = getFlowContainer(pathB, factory);

        processGroups = new HashMap<>();
        VersionedProcessGroup rootPG = snapshotB.getFlowSnapshot().getFlowContents();
        processGroups.put(rootPG.getIdentifier(), rootPG);
        registerProcessGroups(rootPG);

        String plainFlowName = "";
        if (snapshotA != null && snapshotA.getFlowSnapshot().getFlow() != null) {
            plainFlowName = snapshotA.getFlowSnapshot().getFlow().getName();
        } else if (snapshotB.getFlowSnapshot().getFlow() != null) {
            plainFlowName = snapshotB.getFlowSnapshot().getFlow().getName();
        }

        flowName = plainFlowName.isEmpty() ? "Unnamed Flow" : "`" + plainFlowName + "`";

        if (checkstyleEnabled) {
            checkstyleViolations = FlowCheckstyle.getCheckstyleViolations(snapshotB, plainFlowName, rulesConfig);
        }

        if (noOriginalFlow) {
            // we have executed checkstyle if enabled
            // no original flow, so we are not comparing with anything
            return null;
        }

        // identifier is null for parameter contexts, and we know that names are unique so setting name as id
        snapshotA.getFlowSnapshot().getParameterContexts().values().forEach(pc -> pc.setIdentifier(pc.getName()));
        snapshotB.getFlowSnapshot().getParameterContexts().values().forEach(pc -> pc.setIdentifier(pc.getName()));

        final FlowComparator flowComparator = new StandardFlowComparator(
                new StandardComparableDataFlow(
                        "Flow A",
                        snapshotA.getFlowSnapshot().getFlowContents(),
                        null,
                        null,
                        null,
                        new HashSet<>(snapshotA.getFlowSnapshot().getParameterContexts().values()),
                        null,
                        null
                        ),
                new StandardComparableDataFlow(
                        "Flow B",
                        snapshotB.getFlowSnapshot().getFlowContents(),
                        null,
                        null,
                        null,
                        new HashSet<>(snapshotB.getFlowSnapshot().getParameterContexts().values()),
                        null,
                        null
                        ),
                Collections.emptySet(),
                new ConciseEvolvingDifferenceDescriptor(),
                Function.identity(),
                VersionedComponent::getIdentifier,
                FlowComparatorVersionedStrategy.DEEP
            );

        parameterContexts = snapshotB.getFlowSnapshot().getParameterContexts();

        final SortedSet<FlowDifference> sortedDiffs = new TreeSet(new Comparator<FlowDifference>() {
            @Override
            public int compare(FlowDifference o1, FlowDifference o2) {
                String id1 = o1.getComponentA() == null ? String.valueOf(o1.hashCode()) : o1.getComponentA().getInstanceIdentifier() + o1.hashCode();
                String id2 = o2.getComponentA() == null ? String.valueOf(o2.hashCode()) : o2.getComponentA().getInstanceIdentifier() + o2.hashCode();
                return (id1 == null ? String.valueOf(o1.hashCode()) : id1).compareTo(id2 == null ? String.valueOf(o2.hashCode()) : id2);
            }
        });
        sortedDiffs.addAll(flowComparator.compare().getDifferences());

        return sortedDiffs;
    }

    private static void registerProcessGroups(VersionedProcessGroup rootPG) {
        Set<VersionedProcessGroup> childPGs = rootPG.getProcessGroups();
        for (VersionedProcessGroup pg : childPGs) {
            processGroups.put(pg.getIdentifier(), pg);
            registerProcessGroups(pg);
        }
    }

    static FlowSnapshotContainer getFlowContainer(final String path, final JsonFactory factory) throws IOException {
        final File snapshotFile = new File(path);
        try (final JsonParser parser = factory.createParser(snapshotFile)) {
            final RegisteredFlowSnapshot snapshot = parser.readValueAs(RegisteredFlowSnapshot.class);
            return new FlowSnapshotContainer(snapshot);
        }
    }

    static String printComponent(final VersionedComponent component) {
        String result = component.getComponentType().getTypeName();

        if (component instanceof VersionedConfigurableExtension) {
            result += " of type `" + substringAfterLast(((VersionedConfigurableExtension) component).getType(), ".") + "`";
        }

        result += (isEmpty(component.getName()) ? "" : " named `" + component.getName() + "`");

        return result;
    }

    static String printParameterContext(final VersionedParameterContext pc) {
        final Map<String, String> parameters = new HashMap<>();
        for (VersionedParameter p : pc.getParameters()) {
            if (p.isSensitive()) {
                parameters.put(p.getName(), "<Sensitive Value>");
            } else {
                parameters.put(p.getName(), p.getValue());
            }
        }
        return parameters.toString();
    }

    static void printConfigurableExtensionProperties(final VersionedConfigurableExtension proc) {
        for (String key : proc.getProperties().keySet()) {
            System.out.println("  - `" + key + "` = `" + proc.getProperties().get(key) + "`");
        }
    }

    static String printProcessorConf(final VersionedProcessor proc) {
        return "`" + proc.getExecutionNode() + "` nodes, `" + proc.getConcurrentlySchedulableTaskCount() + "` concurrent tasks, `"
                + proc.getRunDurationMillis() + "ms` run duration, `" + proc.getBulletinLevel() + "` bulletin level, `"
                + proc.getSchedulingStrategy() + "` (`" + proc.getSchedulingPeriod() + "`), `"
                + proc.getPenaltyDuration() + "` penalty duration, `" + proc.getYieldDuration() + "` yield duration";
    }

    static void printConnection(final VersionedConnection connection) {
        String message;
        if (connection.getSource().getId().equals(connection.getDestination().getId())) {
            message = "- A self-loop connection `"
                    + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                    + "` has been added on `" + connection.getSource().getName() + "`";
        } else {
            message = "- A connection `"
                    + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                    + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                    + "` has been added";
        }

        List<String> nonDefaultConfigurations = new ArrayList<>();

        if (!connection.getLoadBalanceStrategy().equals("DO_NOT_LOAD_BALANCE")) {
            String lbConfiguration = "load balancing strategy `" + connection.getLoadBalanceStrategy() + "`";
            if (connection.getLoadBalanceStrategy().equals("PARTITION_BY_ATTRIBUTE")) {
                lbConfiguration += " and partitioning attribute `" + connection.getPartitioningAttribute() + "`";
            }
            if (!connection.getLoadBalanceCompression().equals("DO_NOT_COMPRESS")) {
                lbConfiguration += " and load balancing compression `" + connection.getLoadBalanceCompression() + "`";
            }
            nonDefaultConfigurations.add(lbConfiguration);
        }

        if (!connection.getPrioritizers().isEmpty()) {
            nonDefaultConfigurations.add("prioritizers `" + connection.getPrioritizers() + "`");
        }

        if (!connection.getFlowFileExpiration().equals("0 sec")) {
            nonDefaultConfigurations.add("FlowFile expiration of `" + connection.getFlowFileExpiration() + "`");
        }

        if (!connection.getBackPressureDataSizeThreshold().equals("1 GB")) {
            nonDefaultConfigurations.add("backpressure data size threshold of `" + connection.getBackPressureDataSizeThreshold() + "`");
        }

        if (connection.getBackPressureObjectThreshold() != 10000) {
            nonDefaultConfigurations.add("backpressure object threshold of `" + connection.getBackPressureObjectThreshold() + "`");
        }

        if (nonDefaultConfigurations.size() > 0) {
            message += ". The connection is configured with " + String.join(", ", nonDefaultConfigurations);
        }

        System.out.println(message);
    }

    static String printVFCChanges(VersionedFlowCoordinates vfcBefore, VersionedFlowCoordinates vfcAfter) {
        List<String> changes = new ArrayList<>();
        processVFCChange(vfcBefore.getBranch(), vfcAfter.getBranch(), "branch", changes);
        processVFCChange(vfcBefore.getBucketId(), vfcAfter.getBucketId(), "bucket", changes);
        processVFCChange(vfcBefore.getFlowId(), vfcAfter.getFlowId(), "flow ID", changes);
        processVFCChange(vfcBefore.getVersion(), vfcAfter.getVersion(), "version", changes);
        processVFCChange(vfcBefore.getRegistryId(), vfcAfter.getRegistryId(), "registry ID", changes);
        processVFCChange(vfcBefore.getStorageLocation(), vfcAfter.getStorageLocation(), "storage location", changes);
        return String.join(", ", changes);
    }

    static void processVFCChange(String valueA, String valueB, String elementName, List<String> changes) {
        if (Objects.equals(valueA, valueB)) {
            if (valueA != null) {
                changes.add(elementName + " unchanged (`" + valueA + "`)");
            }
        } else {
            changes.add(elementName + " changed from `" + valueA + "` to `" + valueB + "`");
        }
    }

    static String printFromTo(final String from, final String to) {
        if (isMultiline(from) || isMultiline(to)) {
            return "\n```\n" + from + "\n```\nto\n```\n" + to + "\n```";
        }
        return "`" + from + "` to `" + to + "`";
    }

    static boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }

    static boolean isMultiline(String str) {
        if (str == null) {
            return false;
        } else {
            return str.contains("\n") || str.contains("\r");
        }
    }

    static String substringAfterLast(final String str, final String separator) {
        if (str == null || str.isEmpty() || separator == null || separator.isEmpty()) {
            return str;
        }

        final int pos = str.lastIndexOf(separator);

        if (pos == -1) {
            return str;
        }

        return str.substring(pos + separator.length());
    }
}
