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

class FlowGraphTest {

    private static final String CONNECTIONS_BEFORE = "src/test/resources/flow_graph_connections_before.json";
    private static final String CONNECTIONS_AFTER = "src/test/resources/flow_graph_connections_after.json";
    private static final String OVERSIZE_BEFORE = "src/test/resources/flow_graph_oversize_before.json";
    private static final String OVERSIZE_AFTER = "src/test/resources/flow_graph_oversize_after.json";

    @Test
    void testConnectionsFixtureProducesExpectedDiffTypes() throws IOException {
        final Set<FlowDifference> diffs = FlowDiff.getDiff(CONNECTIONS_BEFORE, CONNECTIONS_AFTER, false, null);

        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.DESTINATION_CHANGED)));
        assertTrue(diffs.stream().filter(d -> d.getDifferenceType().equals(DifferenceType.COMPONENT_ADDED)).count() >= 3);
        assertTrue(diffs.stream().anyMatch(d -> d.getDifferenceType().equals(DifferenceType.PROPERTY_CHANGED)));
    }

    @Test
    void testFlowGraphRenderedForStructuralChanges() throws IOException {
        final String output = captureRunWithGraph(CONNECTIONS_BEFORE, CONNECTIONS_AFTER, true);
        final String mermaidBlock = extractMermaidBlockForGroup(output, "GraphFlow");

        assertTrue(output.contains("```mermaid"));
        assertTrue(output.contains("flowchart TB"));
        assertFalse(output.contains("[["));
        assertTrue(mermaidBlock.contains("#91;Child Group#93;"));
        assertTrue(mermaidBlock.contains("#60;RouteOnAttribute#62;"));
        assertTrue(mermaidBlock.contains("stroke:#16a34a,stroke-width:2px;"));
        assertTrue(mermaidBlock.contains("stroke:#dc2626,stroke-width:2px,stroke-dasharray: 5 5;"));
    }

    @Test
    void testFlowGraphEscapesSpecialCharacterLabelsAndPrintsLegendOnce() throws IOException {
        final String output = captureRunWithGraph(CONNECTIONS_BEFORE, CONNECTIONS_AFTER, true);
        final String mermaidBlock = extractMermaidBlockForGroup(output, "GraphFlow");

        assertTrue(mermaidBlock.contains("Weird #quot;Node#quot; #60;x#62; #35;1 #124; #91;y#93;"));
        assertFalse(findLineContaining(mermaidBlock, "Weird ").contains("Weird \"Node\""));
        assertEquals(1, countOccurrences(output, "Node shape: rectangle = processor"));
    }

    @Test
    void testFlowGraphDisabledOmitsMermaidOutput() throws IOException {
        final String output = captureRunWithGraph(CONNECTIONS_BEFORE, CONNECTIONS_AFTER, false);

        assertFalse(output.contains("```mermaid"));
    }

    @Test
    void testOversizeGraphIsSuppressed() throws IOException {
        final Set<FlowDifference> diffs = FlowDiff.getDiff(OVERSIZE_BEFORE, OVERSIZE_AFTER, false, null);
        final String output = captureRunWithGraph(OVERSIZE_BEFORE, OVERSIZE_AFTER, true);

        assertEquals(22, diffs.stream().filter(d -> d.getDifferenceType().equals(DifferenceType.COMPONENT_ADDED)).count());
        assertTrue(output.contains("Graph omitted (too large to render)."));
    }

    @Test
    void testPropertyOnlyGroupDoesNotRenderMermaidBlock() throws IOException {
        final String output = captureRunWithGraph(CONNECTIONS_BEFORE, CONNECTIONS_AFTER, true);
        final String configSection = extractGroupSection(output, "GraphFlow > Config Group");

        assertTrue(configSection.contains("File Size"));
        assertFalse(configSection.contains("```mermaid"));
    }

    private static String captureRunWithGraph(final String before, final String after, final boolean flowGraphEnabled)
            throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final PrintStream orig = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            FlowDiff.run(new String[] {
                    before,
                    after,
                    "",
                    "",
                    "",
                    "false",
                    "",
                    "false",
                    "",
                    Boolean.toString(flowGraphEnabled)
            });
        } finally {
            System.setOut(orig);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static String extractMermaidBlockForGroup(final String output, final String groupPath) {
        final String section = extractGroupSection(output, groupPath);
        final int mermaidStart = section.indexOf("```mermaid\n");
        final int mermaidEnd = section.indexOf("```", mermaidStart + "```mermaid\n".length());
        return section.substring(mermaidStart, mermaidEnd);
    }

    private static String extractGroupSection(final String output, final String groupPath) {
        final String header = "**`" + groupPath + "`**";
        final int start = output.indexOf(header);
        assertTrue(start >= 0, "Missing group header: " + groupPath);

        final int nextHeader = output.indexOf("**`", start + header.length());
        return nextHeader >= 0 ? output.substring(start, nextHeader) : output.substring(start);
    }

    private static String findLineContaining(final String text, final String needle) {
        for (final String line : text.split("\\R")) {
            if (line.contains(needle)) {
                return line;
            }
        }
        return "";
    }

    private static int countOccurrences(final String text, final String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
