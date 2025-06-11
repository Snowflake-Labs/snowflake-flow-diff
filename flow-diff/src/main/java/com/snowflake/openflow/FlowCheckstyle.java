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

import org.apache.nifi.flow.VersionedParameter;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlowCheckstyle {

    public static final String RULE_CONCURRENT_TASKS = "concurrentTasks";
    public static final String RULE_SNAPSHOT_METADATA = "snapshotMetadata";
    public static final String RULE_EMPTY_PARAMETER = "emptyParameter";
    public static final List<String> DEFAULT_CHECKSTYLE_RULES = List.of(
            RULE_CONCURRENT_TASKS,
            RULE_SNAPSHOT_METADATA,
            RULE_EMPTY_PARAMETER);

    public static List<String> getCheckstyleViolations(final FlowSnapshotContainer flowSnapshotContainer, final String flowName, final CheckstyleRulesConfig config) {
        final List<String> violations = new ArrayList<>();
        VersionedProcessGroup rootProcessGroup = flowSnapshotContainer.getFlowSnapshot().getFlowContents();

        final List<String> includes = config == null || config.include() == null ? DEFAULT_CHECKSTYLE_RULES : config.include();
        final List<String> excludes = config == null || config.exclude() == null || config.include() != null ? List.of() : config.exclude();

        // check concurrent tasks
        if (ruleApplies(includes, excludes, config, RULE_CONCURRENT_TASKS, flowName)) {
            violations.addAll(checkConcurrentTasks(rootProcessGroup, config == null ? null : config.rules().get(RULE_CONCURRENT_TASKS), flowName));
        }

        // check that snapshot metadata is present
        if (ruleApplies(includes, excludes, config, RULE_SNAPSHOT_METADATA, flowName)) {
            violations.addAll(checkSnapshotMetadata(flowSnapshotContainer));
        }

        // check that no parameter is set to empty string
        if (ruleApplies(includes, excludes, config, RULE_EMPTY_PARAMETER, flowName)) {
            violations.addAll(checkEmptyParameters(flowSnapshotContainer));
        }

        return violations;
    }

    private static List<String> checkEmptyParameters(FlowSnapshotContainer flowSnapshotContainer) {
        final List<String> violations = new ArrayList<>();
        final List<VersionedParameter> parameters = flowSnapshotContainer.getFlowSnapshot()
                .getParameterContexts()
                .values()
                .stream()
                .flatMap(context -> context.getParameters().stream())
                .toList();

        for (VersionedParameter parameter : parameters) {
            if (parameter.getValue() != null && parameter.getValue().isEmpty()) {
                violations.add("Parameter named `" + parameter.getName() + "` is set to empty string");
            }
        }

        return violations;
    }

    private static List<String> checkSnapshotMetadata(FlowSnapshotContainer flowSnapshotContainer) {
        final List<String> violations = new ArrayList<>();
        if (flowSnapshotContainer.getFlowSnapshot().getSnapshotMetadata() == null) {
            violations.add("Flow snapshot metadata is missing");
        }
        return violations;
    }

    private static List<String> checkConcurrentTasks(VersionedProcessGroup processGroup,
            CheckstyleRulesConfig.RuleConfig ruleConfig,
            String flowName) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(checkConcurrentTasks(childGroup, ruleConfig, flowName));
        }

        int limit = 2;

        if (ruleConfig != null && ruleConfig.parameters() != null && ruleConfig.parameters().get("limit") != null) {
            limit = ((Number) ruleConfig.parameters().get("limit")).intValue();
        }

        if (ruleConfig != null && ruleConfig.overrides() != null) {
            for (Map.Entry<String, Map<String, Object>> entry : ruleConfig.overrides().entrySet()) {
                if (flowName != null && flowName.matches(entry.getKey())) {
                    Object val = entry.getValue().get("limit");
                    if (val != null) {
                        limit = ((Number) val).intValue();
                    }
                }
            }
        }

        for (final VersionedProcessor processor : processGroup.getProcessors()) {
            final int concurrentTasks = processor.getConcurrentlySchedulableTaskCount();
            if (concurrentTasks > limit) {
                violations.add("Processor named `" + processor.getName() + "` is configured with " + concurrentTasks + " concurrent tasks");
            }
        }

        return violations;
    }

    private static boolean ruleApplies(final List<String> includes, final List<String> excludes, CheckstyleRulesConfig config, String ruleName, String flowName) {
        if (!includes.contains(ruleName) || excludes.contains(ruleName)) {
            return false;
        }

        if (config == null || config.rules() == null) {
            return true;
        }

        CheckstyleRulesConfig.RuleConfig ruleConfig = config.rules().get(ruleName);

        if (ruleConfig == null || ruleConfig.exclude() == null) {
            return true;
        }

        if (flowName == null) {
            return true;
        }

        return ruleConfig.exclude().stream().noneMatch(flowName::matches);
    }

}
