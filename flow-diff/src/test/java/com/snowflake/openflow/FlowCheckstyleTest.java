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
import org.apache.nifi.registry.flow.FlowSnapshotContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
