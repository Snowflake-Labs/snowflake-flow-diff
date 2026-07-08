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

import org.apache.nifi.registry.flow.diff.DifferenceType;
import org.apache.nifi.registry.flow.diff.FlowDifference;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowDiffTest {

    @Test
    void testDiffV1V2() throws IOException {
        String flowV1 = "src/test/resources/flow_v1_initial.json";
        String flowV2 = "src/test/resources/flow_v2_added_component.json";
        Set<FlowDifference> diffs = FlowDiff.getDiff(flowV1, flowV2, false, null);
        assertEquals(diffs.size(), 3);
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.POSITION_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.COMPONENT_ADDED)));
    }

    @Test
    void testDiffV2V3() throws IOException {
        String flowV2 = "src/test/resources/flow_v2_added_component.json";
        String flowV3 = "src/test/resources/flow_v3_config_changes.json";
        Set<FlowDifference> diffs = FlowDiff.getDiff(flowV2, flowV3, false, null);
        assertEquals(diffs.size(), 13);
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.RUN_SCHEDULE_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.BACKPRESSURE_DATA_SIZE_THRESHOLD_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.FLOWFILE_EXPIRATION_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.BULLETIN_LEVEL_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.CONCURRENT_TASKS_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PENALTY_DURATION_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PARAMETER_CONTEXT_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.BACKPRESSURE_OBJECT_THRESHOLD_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.AUTO_TERMINATED_RELATIONSHIPS_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.LOAD_BALANCE_STRATEGY_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.LOAD_BALANCE_COMPRESSION_CHANGED)));
    }

    @Test
    void testDiffV3V4() throws IOException {
        String flowV3 = "src/test/resources/flow_v3_config_changes.json";
        String flowV4 = "src/test/resources/flow_v4_parameters.json";
        Set<FlowDifference> diffs = FlowDiff.getDiff(flowV3, flowV4, false, null);
        assertEquals(diffs.size(), 14);
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.INHERITED_CONTEXTS_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PARAMETER_ADDED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.SCHEDULING_STRATEGY_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PROPERTY_PARAMETERIZED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.NAME_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.BUNDLE_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.COMPONENT_REMOVED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.RUN_SCHEDULE_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PARAMETER_REMOVED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.DESTINATION_CHANGED)));
    }

    @Test
    void testDiffV4V5() throws IOException {
        String flowV4 = "src/test/resources/flow_v4_parameters.json";
        String flowV5 = "src/test/resources/flow_v5_property_parameter.json";
        Set<FlowDifference> diffs = FlowDiff.getDiff(flowV4, flowV5, false, null);
        assertEquals(diffs.size(), 7);
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PROPERTY_PARAMETERIZATION_REMOVED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.SCHEDULED_STATE_CHANGED)));
    }

    @Test
    void testDiffV3V5() throws IOException {
        String flowV3 = "src/test/resources/flow_v3_config_changes.json";
        String flowV5 = "src/test/resources/flow_v5_property_parameter.json";
        Set<FlowDifference> diffs = FlowDiff.getDiff(flowV3, flowV5, false, null);
        assertEquals(diffs.size(), 13);
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PROPERTY_ADDED)));
    }

    @Test
    void testDiffV5V6() throws IOException {
        String flowV5 = "src/test/resources/flow_v5_property_parameter.json";
        String flowV6 = "src/test/resources/flow_v6_parameter_value.json";
        Set<FlowDifference> diffs = FlowDiff.getDiff(flowV5, flowV6, false, null);
        assertEquals(diffs.size(), 11);
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PROPERTY_ADDED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PROPERTY_REMOVED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PARAMETER_VALUE_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PARAMETER_ADDED)));
    }

    @Test
    void testMain() throws IOException {
        // Arguments: flowA, flowB, token, repository, issuenumber, checkstyle, checkstyle-rules, checkstyle-fail
        final int exitCode = FlowDiff.run(new String[] {
                "src/test/resources/flow_v3_config_changes.json,src/test/resources/flow_v5_property_parameter.json",
                "src/test/resources/flow_v4_parameters.json,src/test/resources/flow_v6_parameter_value.json",
                "", "", "",  // no GitHub API
                "true" });
        assertEquals(0, exitCode);
    }

    @Test
    void testCheckstyleFailExitCode() throws IOException {
        // Arguments: flowA, flowB, token, repository, issuenumber, checkstyle, checkstyle-rules, checkstyle-fail
        final int exitCode = FlowDiff.run(new String[] {
                "src/test/resources/flow_v6_parameter_value.json",
                "src/test/resources/flow_v6_parameter_value.json",
                "", "", "",  // no GitHub API
                "true",
                "src/test/resources/checkstyle_limit1.yaml",
                "true" });
        assertEquals(2, exitCode);
    }

    @Test
    void testNestedGroupsHaveSeparateSections() throws IOException {
        final Set<FlowDifference> diffs = FlowDiff.getDiff(
                "src/test/resources/flow_v8_nested_groups_before.json",
                "src/test/resources/flow_v8_nested_groups_after.json",
                false, null);
        // One PROPERTY_CHANGED in Group A and one COMPONENT_ADDED in Group B
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PROPERTY_CHANGED)));
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.COMPONENT_ADDED)));
        assertEquals(2, diffs.size());
    }

    @Test
    void testGroupedOutputContainsGroupHeaders() throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final PrintStream orig = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            FlowDiff.run(new String[]{
                "src/test/resources/flow_v8_nested_groups_before.json",
                "src/test/resources/flow_v8_nested_groups_after.json",
                "", "", ""
            });
        } finally {
            System.setOut(orig);
        }
        final String output = buf.toString(StandardCharsets.UTF_8);
        // Both group paths must appear as bold headers
        assertTrue(output.contains("**`NestedGroupsFlow > Group A`**"), "Group A header missing");
        assertTrue(output.contains("**`NestedGroupsFlow > Group B`**"), "Group B header missing");
        // Group A comes before Group B (alphabetical sort)
        assertTrue(output.indexOf("Group A") < output.indexOf("Group B"), "Groups not in alphabetical order");
        // Each section shows a change count
        assertTrue(output.contains("1 change"), "Change count missing");
        // The property change line belongs under Group A
        final int groupAPos = output.indexOf("**`NestedGroupsFlow > Group A`**");
        final int groupBPos = output.indexOf("**`NestedGroupsFlow > Group B`**");
        final int propertyChangeLine = output.indexOf("File Size");
        assertTrue(propertyChangeLine > groupAPos && propertyChangeLine < groupBPos,
                "File Size change should appear under Group A");
        // The added processor line belongs under Group B
        final int addedProcessorLine = output.indexOf("UpdateAttribute");
        assertTrue(addedProcessorLine > groupBPos, "UpdateAttribute addition should appear under Group B");
    }

    @Test
    void testAddedProcessGroupAppearsUnderItsOwnPath() throws IOException {
        final String output = captureRun(
                "src/test/resources/flow_v9_group_placement_before.json",
                "src/test/resources/flow_v9_group_placement_after.json");
        // The added group is placed under its own path, not under the root section
        assertTrue(output.contains("**`PlacementFlow > Group A`**"), "Added group's own-path header missing");
        final int ownPathHeader = output.indexOf("**`PlacementFlow > Group A`**");
        final int addedLine = output.indexOf("Group A` has been added");
        assertTrue(addedLine > ownPathHeader, "Group A addition should appear under its own path header");
    }

    @Test
    void testRemovedProcessGroupAppearsUnderItsOwnPath() throws IOException {
        final String output = captureRun(
                "src/test/resources/flow_v9_group_placement_after.json",
                "src/test/resources/flow_v9_group_placement_before.json");
        // The removed group is placed under its own path, not under the root section
        assertTrue(output.contains("**`PlacementFlow > Group A`**"), "Removed group's own-path header missing");
        final int ownPathHeader = output.indexOf("**`PlacementFlow > Group A`**");
        final int removedLine = output.indexOf("Group A` has been removed");
        assertTrue(removedLine > ownPathHeader, "Group A removal should appear under its own path header");
    }

    @Test
    void testSingleGroupOutputHasGroupHeader() throws IOException {
        final String output = captureRun(
                "src/test/resources/flow_v1_initial.json",
                "src/test/resources/flow_v2_added_component.json");
        // Single-group flow still gets a group header
        assertTrue(output.contains("**`TestingFlowDiff`**"), "Root process group header missing");
        // No Parameter Contexts section for this diff (no param context changes)
        assertFalse(output.contains(FlowDiff.PARAMETER_CONTEXTS_SECTION), "Unexpected parameter contexts section");
    }

    private static String captureRun(final String before, final String after) throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final PrintStream orig = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            FlowDiff.run(new String[]{before, after, "", "", ""});
        } finally {
            System.setOut(orig);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}
