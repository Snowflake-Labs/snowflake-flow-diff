/*
 * Copyright 2026 Snowflake Inc.
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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig.RuleConfig;
import com.snowflake.openflow.checkstyle.DefaultCheckstyleRules;
import org.apache.nifi.flow.ConnectableComponent;
import org.apache.nifi.flow.VersionedConnection;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowCheckstyleTest {

    private static JsonFactory jsonFactory;

    @BeforeAll
    static void setup() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        jsonFactory = new JsonFactory(objectMapper);
    }

    @Test
    void testCustomLimit() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_limit1.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("InvokeHTTP")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("UpdateAttribute")));
    }

    @Test
    void testConcurrentTasksComponentExclusion() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_component_exclusions_concurrent.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("InvokeHTTP") && v.contains("1a59f65f-8b3a-3db9-982e-e0d334bd7e9c")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("2d8da922-fd1f-3519-9d54-6482dfd42c56")));
    }

    @Test
    void testOverride() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_override.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(0, violations.size());
    }

    @Test
    void testExclude() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_exclude.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Flow snapshot metadata is missing")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("is set to empty string")));
    }

    @Test
    void testEmptyParameters() throws IOException {
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_emptyParameters.yaml");
        FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Parameter named `secured` is set with value `` and is not configured as a parameter that should have a default value")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("Parameter named `should Have Default` is `null` even though it should have a default value")));
        assertTrue(violations.stream()
                .anyMatch(v -> v.contains("Parameter named `should Not Have Default` is set with value `default` and is not configured as a parameter that should have a default value")));

        container = FlowDiff.getFlowContainer("src/test/resources/flow_v5_property_parameter.json", jsonFactory);
        violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Parameter named `addedParam` is set with value `addedValue` and is not configured as a parameter that should have a default value")));
    }

    @Test
    void testUnusedParameter() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("unusedParameter"), null, null);
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Parameter named `newSensitiveParam` is not used anywhere in the flow")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("Parameter named `secured` is not used anywhere in the flow")));
    }

    @Test
    void testNoSelfLoop() throws IOException {
        FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("noSelfLoop"), null, null);
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Component named `UpdateAttribute` of type `PROCESSOR` has a self-loop connection")));
    }

    @Test
    void testNoSelfLoopComponentExclusion() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_component_exclusions_selfloop.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(0, violations.size());
    }

    @Test
    void testEnforcePrioritizerNoArgument() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("enforcePrioritizer"), null, null);
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(0, violations.size());
    }

    @Test
    void testEnforcePrioritizerWithGlobalParameter() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final RuleConfig ruleConfig = new CheckstyleRulesConfig.RuleConfig(Map.of("prioritizers", "org.apache.nifi.prioritizer.FirstInFirstOutPrioritizer"), null, null, null);
        CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("enforcePrioritizer"), null, Map.of("enforcePrioritizer", ruleConfig));
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(3, violations.size());
    }

    @Test
    void testEnforcePrioritizerComponentExclusion() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_component_exclusions_prioritizer.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().noneMatch(v -> v.contains("a760d0b0-51e7-34af-922a-47366dfb2892")));
    }

    @Test
    void testEnforcePrioritizerWithOverrideParameter() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        final RuleConfig ruleConfig = new CheckstyleRulesConfig.RuleConfig(null, Map.of(".*", Map.of("prioritizers", "org.apache.nifi.prioritizer.FirstInFirstOutPrioritizer")), null, null);
        CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("enforcePrioritizer"), null, Map.of("enforcePrioritizer", ruleConfig));
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(3, violations.size());
    }

    @Test
    void testBackpressureThresholdViolations() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);

        container.getFlowSnapshot().getFlowContents().getConnections().iterator().next().setBackPressureDataSizeThreshold("0 B");
        container.getFlowSnapshot().getFlowContents().getConnections().iterator().next().setBackPressureObjectThreshold(0L);

        final CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("backpressureThreshold"), null, null);
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("data size backpressure threshold")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("object count backpressure threshold")));
    }

    @Test
    void testBackpressureThresholdComponentExclusion() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);

        container.getFlowSnapshot().getFlowContents().getConnections().iterator().next().setBackPressureDataSizeThreshold("0 B");
        container.getFlowSnapshot().getFlowContents().getConnections().iterator().next().setBackPressureObjectThreshold(0L);

        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_component_exclusions_backpressure.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(0, violations.size());
    }

    @Test
    void testBackpressureThresholdNoViolationsWhenPositive() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);

        final CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("backpressureThreshold"), null, null);
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(0, violations.size());
    }

    @Test
    void testProcessorNamingViolations() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_processor_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("InvokeHTTP") && v.contains("does not match") && v.contains("proc-invoke-http-001")));
    }

    @Test
    void testProcessorNamingNoViolationsWhenCompliant() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_processor_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertTrue(violations.stream().noneMatch(v -> v.contains("GENERATE_FLOW_FILE")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("UPDATE_ATTRIBUTE")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("CUSTOMER_SEL")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("CUSTOMER_ISTG")));
    }

    @Test
    void testProcessorNamingDefaultPattern() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_processor_naming_default.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("InvokeHTTP") && v.contains("does not match")));
    }

    @Test
    void testProcessorNamingComponentExclusion() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_naming_component_exclusions.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(0, violations.size());
    }

    @Test
    void testProcessorNamingOverride() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_naming_overrides.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(0, violations.size());
    }

    @Test
    void testControllerServiceNamingViolations() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_controller_service_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Snowconnection_DEV") && v.contains("does not match") && v.contains("cs-dbcp-invalid-001")));
    }

    @Test
    void testControllerServiceNamingValidNames() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_controller_service_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertTrue(violations.stream().noneMatch(v -> v.contains("acme_prod_mssql_mds")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("csv_record_writer")));
    }

    @Test
    void testParameterContextNamingViolations() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_parameter_context_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Test Parameter Context") && v.contains("does not match")));
    }

    @Test
    void testParameterContextNamingExclude() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_parameter_context_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertTrue(violations.stream().noneMatch(v -> v.contains("common_parameter_context")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("acme_prod_postgres_rbs_inventorydb_secrets")));
    }

    @Test
    void testParameterProviderNamingViolations() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_parameter_provider_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Acme_Prod_Mssql_Secrets") && v.contains("does not match") && v.contains("pp-invalid-001")));
    }

    @Test
    void testParameterProviderNamingValidNames() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_parameter_provider_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertTrue(violations.stream().noneMatch(v -> v.contains("acme_prod_mssql_landmark_secrets")));
    }

    @Test
    void testProcessorNamingNestedProcessGroup() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming_nested.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_processor_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("InvokeHTTP") && v.contains("does not match") && v.contains("nested-proc-invoke-http-001")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("nested-proc-exec-sql-001")));
    }

    @Test
    void testControllerServiceNamingNestedProcessGroup() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming_nested.json", jsonFactory);
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_controller_service_naming.yaml");
        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Snowconnection_DEV") && v.contains("does not match") && v.contains("nested-cs-dbcp-invalid-001")));
        assertTrue(violations.stream().noneMatch(v -> v.contains("nested-cs-dbcp-valid-001")));
    }

    @Test
    void testNamingRulesRejectNonMapPatterns() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);
        final RuleConfig ruleConfig = new CheckstyleRulesConfig.RuleConfig(Map.of("patterns", "not-a-map"), null, null, null);
        final CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("processorNaming"), null, Map.of("processorNaming", ruleConfig));

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config));
        assertTrue(exception.getMessage().contains("'patterns' must be a map"));
    }

    @Test
    void testNamingRulesNoViolationsWithoutConfig() throws IOException {
        final FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v7_naming.json", jsonFactory);

        CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("processorNaming"), null, null);
        assertEquals(0, FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config).size());

        config = new CheckstyleRulesConfig(List.of("controllerServiceNaming"), null, null);
        assertEquals(0, FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config).size());

        config = new CheckstyleRulesConfig(List.of("parameterContextNaming"), null, null);
        assertEquals(0, FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config).size());

        config = new CheckstyleRulesConfig(List.of("parameterProviderNaming"), null, null);
        assertEquals(0, FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config).size());
    }

    @Test
    void testRemovedConnectionViolationUsesRelationshipFallback() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", removedConnectionConfig());

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("removed-connection-root-001"));
        assertTrue(violations.get(0).contains("GenerateFlowFile"));
        assertTrue(violations.get(0).contains("UpdateAttribute"));
        assertTrue(violations.get(0).contains("[success]"));
    }

    @Test
    void testRemovedConnectionViolationUsesPreviousConnectionName() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");

        onlyRootConnection(previous).setName("critical-path");

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", removedConnectionConfig());

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("critical-path"));
        assertTrue(violations.get(0).contains("removed-connection-root-001"));
        assertTrue(violations.get(0).contains("GenerateFlowFile"));
        assertTrue(violations.get(0).contains("UpdateAttribute"));
        assertFalse(violations.get(0).contains("[success]"));
    }

    @Test
    void testRemovedConnectionViolationUsesEmptyRelationshipFallbackWhenRelationshipsAreNull() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");

        onlyRootConnection(previous).setSelectedRelationships(null);

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", removedConnectionConfig());

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("[]"));
        assertFalse(violations.get(0).contains("null"));
    }

    @Test
    void testRemovedConnectionViolationForMultipleNestedAndDeletedGroupRemovals() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v11_removed_connections_nested_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v11_removed_connections_nested_after.json");

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionNestedFlow", removedConnectionConfig());

        assertEquals(3, violations.size());
        assertEquals(1, countViolationsContaining(violations, "root-removed-connection-001"));
        assertEquals(1, countViolationsContaining(violations, "nested-removed-connection-001"));
        assertEquals(1, countViolationsContaining(violations, "deleted-group-removed-connection-001"));
    }

    @Test
    void testRemovedConnectionRuleIgnoresNullPreviousSnapshot() throws IOException {
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(null, current, "RemovedConnectionFlow", removedConnectionConfig());

        assertEquals(0, violations.size());
    }

    @Test
    void testRemovedConnectionRuleIgnoresRetainedAndAddedConnections() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer retainedCurrent = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer additionCurrent = loadFlow("flow_v10_removed_connection_before.json");

        final List<String> retainedViolations = FlowCheckstyle.getCheckstyleViolations(previous, retainedCurrent, "RemovedConnectionFlow", removedConnectionConfig());
        assertEquals(0, retainedViolations.size());

        final VersionedConnection existingConnection = onlyRootConnection(additionCurrent);
        final Set<VersionedConnection> currentConnections = new LinkedHashSet<>(additionCurrent.getFlowSnapshot().getFlowContents().getConnections());
        currentConnections.add(createConnection(
                "added-connection-001",
                "new-path",
                Set.of("failure"),
                existingConnection.getSource(),
                existingConnection.getDestination()));
        additionCurrent.getFlowSnapshot().getFlowContents().setConnections(currentConnections);

        final List<String> additionViolations = FlowCheckstyle.getCheckstyleViolations(previous, additionCurrent, "RemovedConnectionFlow", removedConnectionConfig());
        assertEquals(0, additionViolations.size());
    }

    @Test
    void testRemovedConnectionRuleIgnoresSameUuidSourceIdChanges() throws IOException {
        assertNoRemovedConnectionViolationForSameUuidChange(connection -> connection.getSource().setId("replacement-source-001"));
    }

    @Test
    void testRemovedConnectionRuleIgnoresSameUuidDestinationIdChanges() throws IOException {
        assertNoRemovedConnectionViolationForSameUuidChange(connection -> connection.getDestination().setId("replacement-destination-001"));
    }

    @Test
    void testRemovedConnectionRuleIgnoresSameUuidConnectionNameChanges() throws IOException {
        assertNoRemovedConnectionViolationForSameUuidChange(connection -> connection.setName("renamed-connection"));
    }

    @Test
    void testRemovedConnectionRuleIgnoresSameUuidRelationshipChanges() throws IOException {
        assertNoRemovedConnectionViolationForSameUuidChange(connection -> connection.setSelectedRelationships(new LinkedHashSet<>(Set.of("retry"))));
    }

    @Test
    void testRemovedConnectionRuleReportsDeleteAndRedrawWithNewUuid() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_before.json");

        final VersionedConnection currentConnection = onlyRootConnection(current);
        current.getFlowSnapshot().getFlowContents().setConnections(new LinkedHashSet<>(Set.of(createConnection(
                "replacement-connection-001",
                currentConnection.getName(),
                currentConnection.getSelectedRelationships(),
                currentConnection.getSource(),
                currentConnection.getDestination()))));

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", removedConnectionConfig());

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("removed-connection-root-001"));
    }

    @Test
    void testRemovedConnectionRuleComponentExclusionUsesPreviousUuid() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_removed_connection_component_exclusion.yaml");

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", config);

        assertEquals(0, violations.size());
    }

    @Test
    void testRemovedConnectionRuleRegistrationAndGlobalActivationControls() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");

        assertEquals(DefaultCheckstyleRules.REMOVED_CONNECTION, DefaultCheckstyleRules.fromId("removedConnection"));
        assertTrue(FlowCheckstyle.DEFAULT_CHECKSTYLE_RULES.contains("removedConnection"));

        final List<String> defaultViolations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", null);
        assertEquals(1, countViolationsContaining(defaultViolations, "removed-connection-root-001"));

        final List<String> includedViolations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", removedConnectionConfig());
        assertEquals(1, countViolationsContaining(includedViolations, "removed-connection-root-001"));

        final CheckstyleRulesConfig excludedConfig = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_removed_connection_global_exclude.yaml");
        final List<String> excludedViolations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", excludedConfig);
        assertEquals(0, countViolationsContaining(excludedViolations, "removed-connection-root-001"));
    }

    @Test
    void testRemovedConnectionRuleFlowNameExclusionMatchesOnlyConfiguredFlows() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");
        final CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_removed_connection_flow_exclude.yaml");

        final List<String> excludedViolations = FlowCheckstyle.getCheckstyleViolations(previous, current, "Prod Removed Flow", config);
        assertEquals(0, excludedViolations.size());

        final List<String> includedViolations = FlowCheckstyle.getCheckstyleViolations(previous, current, "Dev Removed Flow", config);
        assertEquals(1, includedViolations.size());
        assertTrue(includedViolations.get(0).contains("removed-connection-root-001"));
    }

    @Test
    void testExistingRulesReturnSameViolationsThroughThreeAndFourArgumentApis() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v6_parameter_value.json");
        final CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("noSelfLoop"), null, null);

        final List<String> threeArgumentViolations = FlowCheckstyle.getCheckstyleViolations(current, current.getFlowSnapshot().getFlow().getName(), config);
        final List<String> fourArgumentViolations = FlowCheckstyle.getCheckstyleViolations(previous, current, current.getFlowSnapshot().getFlow().getName(), config);

        assertEquals(threeArgumentViolations, fourArgumentViolations);
        assertEquals(1, fourArgumentViolations.size());
        assertTrue(fourArgumentViolations.get(0).contains("UpdateAttribute"));
    }

    @Test
    void testThreeArgumentCheckstyleOverloadDoesNotReportRemovedConnectionViolations() throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_after.json");

        final List<String> fourArgumentViolations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", removedConnectionConfig());
        assertEquals(1, fourArgumentViolations.size());
        assertTrue(fourArgumentViolations.get(0).contains("removed-connection-root-001"));

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(current, "RemovedConnectionFlow", removedConnectionConfig());

        assertEquals(0, violations.size());
    }

    private static FlowSnapshotContainer loadFlow(final String resource) throws IOException {
        return FlowDiff.getFlowContainer("src/test/resources/" + resource, jsonFactory);
    }

    private static CheckstyleRulesConfig removedConnectionConfig() {
        return new CheckstyleRulesConfig(List.of("removedConnection"), null, null);
    }

    private static VersionedConnection onlyRootConnection(final FlowSnapshotContainer container) {
        return container.getFlowSnapshot().getFlowContents().getConnections().iterator().next();
    }

    private static void assertNoRemovedConnectionViolationForSameUuidChange(final Consumer<VersionedConnection> currentMutation) throws IOException {
        final FlowSnapshotContainer previous = loadFlow("flow_v10_removed_connection_before.json");
        final FlowSnapshotContainer current = loadFlow("flow_v10_removed_connection_before.json");

        currentMutation.accept(onlyRootConnection(current));

        final List<String> violations = FlowCheckstyle.getCheckstyleViolations(previous, current, "RemovedConnectionFlow", removedConnectionConfig());

        assertEquals(0, violations.size());
    }

    private static int countViolationsContaining(final List<String> violations, final String expectedFragment) {
        return (int) violations.stream().filter(violation -> violation.contains(expectedFragment)).count();
    }

    private static VersionedConnection createConnection(final String identifier,
            final String name,
            final Set<String> selectedRelationships,
            final ConnectableComponent source,
            final ConnectableComponent destination) {
        final VersionedConnection connection = new VersionedConnection();
        connection.setIdentifier(identifier);
        connection.setGroupIdentifier(source.getGroupId());
        connection.setName(name);
        connection.setSelectedRelationships(new LinkedHashSet<>(selectedRelationships));
        connection.setSource(source);
        connection.setDestination(destination);
        connection.setBackPressureDataSizeThreshold("1 GB");
        connection.setBackPressureObjectThreshold(10000L);
        connection.setFlowFileExpiration("0 sec");
        connection.setPrioritizers(List.of());
        connection.setLoadBalanceStrategy("DO_NOT_LOAD_BALANCE");
        connection.setPartitioningAttribute("");
        connection.setLoadBalanceCompression("DO_NOT_COMPRESS");
        return connection;
    }
}
