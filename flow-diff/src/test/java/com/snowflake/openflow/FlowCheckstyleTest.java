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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

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
        FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_limit1.yaml");
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("InvokeHTTP")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("UpdateAttribute")));
    }

    @Test
    void testOverride() throws IOException {
        FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_override.yaml");
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(0, violations.size());
    }

    @Test
    void testExclude() throws IOException {
        FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_exclude.yaml");
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Flow snapshot metadata is missing")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("is set to empty string")));
    }

    @Test
    void testEmptyParameters() throws IOException {
        CheckstyleRulesConfig config = CheckstyleRulesConfig.fromFile("src/test/resources/checkstyle_emptyParameters.yaml");

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
        FlowSnapshotContainer container = FlowDiff.getFlowContainer("src/test/resources/flow_v6_parameter_value.json", jsonFactory);
        CheckstyleRulesConfig config = new CheckstyleRulesConfig(List.of("unusedParameter"), null, null);
        List<String> violations = FlowCheckstyle.getCheckstyleViolations(container, container.getFlowSnapshot().getFlow().getName(), config);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Parameter named `newSensitiveParam` is not used anywhere in the flow")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("Parameter named `secured` is not used anywhere in the flow")));
    }
}
