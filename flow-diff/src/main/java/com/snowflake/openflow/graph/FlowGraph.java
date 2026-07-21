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
package com.snowflake.openflow.graph;

import org.apache.nifi.flow.ComponentType;
import org.apache.nifi.flow.ConnectableComponent;
import org.apache.nifi.flow.VersionedComponent;
import org.apache.nifi.flow.VersionedConfigurableExtension;
import org.apache.nifi.flow.VersionedConnection;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.registry.flow.diff.FlowDifference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowGraph {

    private static final int MAX_GRAPH_NODES = 20;
    private static final int MAX_GRAPH_EDGES = 40;
    private static final int MAX_GRAPH_RENDERED_CHARS = 45000;

    public static final String LEGEND = "Legend: \uD83D\uDFE9 added \u00b7 \uD83D\uDFE5 removed \u00b7 \uD83D\uDFE7 modified \u00b7 \u2b1c unchanged. Node shape: rectangle = processor, rounded = port, circle = funnel. Second line shows `<processor type>` or `[containing group]`.";

    private final Map<String, VersionedProcessGroup> processGroupsA = new HashMap<>();
    private final Map<String, VersionedProcessGroup> processGroupsB = new HashMap<>();
    private final Map<String, VersionedComponent> graphComponentsA = new HashMap<>();
    private final Map<String, VersionedComponent> graphComponentsB = new HashMap<>();
    private final Map<String, ConnectableComponent> connectableComponentsA = new HashMap<>();
    private final Map<String, ConnectableComponent> connectableComponentsB = new HashMap<>();

    private FlowGraph() {
    }

    public static FlowGraph forFlow(final VersionedProcessGroup rootA, final VersionedProcessGroup rootB) {
        final FlowGraph flowGraph = new FlowGraph();
        if (rootA != null) {
            flowGraph.processGroupsA.put(rootA.getIdentifier(), rootA);
            flowGraph.registerProcessGroups(rootA, flowGraph.processGroupsA);
            flowGraph.registerGraphComponents(rootA, flowGraph.graphComponentsA, flowGraph.connectableComponentsA);
        }
        if (rootB != null) {
            flowGraph.processGroupsB.put(rootB.getIdentifier(), rootB);
            flowGraph.registerProcessGroups(rootB, flowGraph.processGroupsB);
            flowGraph.registerGraphComponents(rootB, flowGraph.graphComponentsB, flowGraph.connectableComponentsB);
        }
        return flowGraph;
    }

    public GraphRender render(final List<FlowDifference> groupDiffs, final String currentGroupId) {
        if (groupDiffs == null || groupDiffs.isEmpty() || currentGroupId == null) {
            return null;
        }

        final GraphCollector collector = new GraphCollector();
        collector.currentGroupId = currentGroupId;
        for (final FlowDifference diff : groupDiffs) {
            collectGraphDiff(diff, collector);
        }

        if (!collector.hasStructuralChange) {
            return null;
        }

        if (collector.nodes.size() < 2 && collector.edges.isEmpty()) {
            return null;
        }

        if (collector.nodes.size() > MAX_GRAPH_NODES || collector.edges.size() > MAX_GRAPH_EDGES) {
            return new GraphRender(true, null);
        }

        final String mermaid = renderGraph(collector);
        if (mermaid.length() > MAX_GRAPH_RENDERED_CHARS) {
            return new GraphRender(true, null);
        }

        return new GraphRender(false, mermaid);
    }

    public record GraphRender(boolean omitted, String mermaid) {
    }

    private enum GraphState {
        CONTEXT,
        MODIFIED,
        ADDED,
        REMOVED
    }

    private enum ComponentKind {
        PROCESSOR,
        PORT,
        FUNNEL,
        GROUP,
        OTHER
    }

    private static final class GraphNode {
        private final String componentId;
        private final String mermaidId;
        private final String label;
        private final ComponentKind kind;
        private String displayLabel;
        private GraphState state;

        private GraphNode(final String componentId, final String mermaidId, final String label,
                final ComponentKind kind, final GraphState state) {
            this.componentId = componentId;
            this.mermaidId = mermaidId;
            this.label = label;
            this.kind = kind;
            this.displayLabel = label;
            this.state = state;
        }
    }

    private static final class GraphEdge {
        private final String sourceId;
        private final String destinationId;
        private final String label;
        private final GraphState state;

        private GraphEdge(final String sourceId, final String destinationId, final String label, final GraphState state) {
            this.sourceId = sourceId;
            this.destinationId = destinationId;
            this.label = label;
            this.state = state;
        }
    }

    private static final class GraphCollector {
        private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
        private final List<GraphEdge> edges = new ArrayList<>();
        private int nodeSequence = 0;
        private boolean hasStructuralChange;
        private String currentGroupId;
    }

    private void collectGraphDiff(final FlowDifference diff, final GraphCollector collector) {
        switch (diff.getDifferenceType()) {
        case COMPONENT_ADDED: {
            final VersionedComponent component = diff.getComponentB();
            if (component == null) {
                return;
            }
            if (component.getComponentType().equals(ComponentType.CONNECTION)) {
                collector.hasStructuralChange = true;
                addConnectionGraph((VersionedConnection) component, GraphState.ADDED, collector);
                return;
            }
            if (isGraphableNode(component)) {
                addGraphNode(component.getIdentifier(), component, null, GraphState.ADDED, collector);
                if (isStructuralNodeComponent(component)) {
                    collector.hasStructuralChange = true;
                }
            }
            return;
        }
        case COMPONENT_REMOVED: {
            final VersionedComponent component = diff.getComponentA();
            if (component == null) {
                return;
            }
            if (component.getComponentType().equals(ComponentType.CONNECTION)) {
                collector.hasStructuralChange = true;
                addConnectionGraph((VersionedConnection) component, GraphState.REMOVED, collector);
                return;
            }
            if (isGraphableNode(component)) {
                addGraphNode(component.getIdentifier(), component, null, GraphState.REMOVED, collector);
                if (isStructuralNodeComponent(component)) {
                    collector.hasStructuralChange = true;
                }
            }
            return;
        }
        case DESTINATION_CHANGED: {
            final VersionedConnection connection = diff.getComponentA() instanceof VersionedConnection
                    ? (VersionedConnection) diff.getComponentA()
                    : (VersionedConnection) diff.getComponentB();
            if (connection == null || connection.getSource() == null) {
                return;
            }
            collector.hasStructuralChange = true;
            final ConnectableComponent oldDestination = diff.getValueA() instanceof ConnectableComponent
                    ? (ConnectableComponent) diff.getValueA()
                    : connection.getDestination();
            final ConnectableComponent newDestination = diff.getValueB() instanceof ConnectableComponent
                    ? (ConnectableComponent) diff.getValueB()
                    : connection.getDestination();
            addGraphNode(connection.getSource().getId(), null, connection.getSource(), classifyNodeState(connection.getSource().getId(), null), collector);
            final String destRel = relationshipLabel(connection);
            if (oldDestination != null) {
                addGraphNode(oldDestination.getId(), null, oldDestination, GraphState.REMOVED, collector);
                addGraphEdge(connection.getSource().getId(), oldDestination.getId(), destRel, GraphState.REMOVED, collector);
            }
            if (newDestination != null) {
                addGraphNode(newDestination.getId(), null, newDestination, GraphState.ADDED, collector);
                addGraphEdge(connection.getSource().getId(), newDestination.getId(), destRel, GraphState.ADDED, collector);
            }
            return;
        }
        case BACKPRESSURE_DATA_SIZE_THRESHOLD_CHANGED:
        case BACKPRESSURE_OBJECT_THRESHOLD_CHANGED:
        case LOAD_BALANCE_STRATEGY_CHANGED:
        case LOAD_BALANCE_COMPRESSION_CHANGED:
        case FLOWFILE_EXPIRATION_CHANGED:
        case PARTITIONING_ATTRIBUTE_CHANGED:
        case PRIORITIZERS_CHANGED: {
            final VersionedConnection connection = diff.getComponentA() instanceof VersionedConnection
                    ? (VersionedConnection) diff.getComponentA()
                    : (VersionedConnection) diff.getComponentB();
            if (connection != null) {
                addConnectionGraph(connection, GraphState.MODIFIED, collector);
            }
            return;
        }
        case SELECTED_RELATIONSHIPS_CHANGED: {
            // Use componentB (new state) so the edge label reflects the updated relationships.
            final VersionedConnection connection = diff.getComponentB() instanceof VersionedConnection
                    ? (VersionedConnection) diff.getComponentB()
                    : (VersionedConnection) diff.getComponentA();
            if (connection != null) {
                addConnectionGraph(connection, GraphState.MODIFIED, collector);
            }
            return;
        }
        default: {
            final VersionedComponent component = diff.getComponentB() != null ? diff.getComponentB() : diff.getComponentA();
            if (component != null && isGraphableNode(component) && isNodeGraphDiff(diff)) {
                addGraphNode(component.getIdentifier(), component, null, GraphState.MODIFIED, collector);
            }
        }
        }
    }

    private boolean isGraphableNode(final VersionedComponent component) {
        if (component == null || component.getComponentType() == null) {
            return false;
        }
        return switch (component.getComponentType()) {
            case PROCESSOR, INPUT_PORT, OUTPUT_PORT, FUNNEL -> true;
            default -> false;
        };
    }

    private boolean isStructuralNodeComponent(final VersionedComponent component) {
        if (component == null || component.getComponentType() == null) {
            return false;
        }
        return switch (component.getComponentType()) {
            case PROCESSOR, INPUT_PORT, OUTPUT_PORT, FUNNEL -> true;
            default -> false;
        };
    }

    private boolean isNodeGraphDiff(final FlowDifference diff) {
        return switch (diff.getDifferenceType()) {
            case PROPERTY_CHANGED,
                    CONCURRENT_TASKS_CHANGED,
                    BULLETIN_LEVEL_CHANGED,
                    RUN_DURATION_CHANGED,
                    RUN_SCHEDULE_CHANGED,
                    AUTO_TERMINATED_RELATIONSHIPS_CHANGED,
                    PENALTY_DURATION_CHANGED,
                    SCHEDULING_STRATEGY_CHANGED,
                    NAME_CHANGED,
                    PROPERTY_ADDED,
                    PROPERTY_PARAMETERIZED,
                    PROPERTY_PARAMETERIZATION_REMOVED,
                    SCHEDULED_STATE_CHANGED,
                    PROPERTY_REMOVED,
                    YIELD_DURATION_CHANGED,
                    RETRY_COUNT_CHANGED,
                    MAX_BACKOFF_PERIOD_CHANGED,
                    BACKOFF_MECHANISM_CHANGED,
                    COMMENTS_CHANGED,
                    RETRIED_RELATIONSHIPS_CHANGED,
                    EXECUTION_MODE_CHANGED,
                    PROPERTY_SENSITIVITY_CHANGED,
                    FLOWFILE_CONCURRENCY_CHANGED,
                    FLOWFILE_OUTBOUND_POLICY_CHANGED,
                    VERSIONED_FLOW_COORDINATES_CHANGED -> true;
            default -> false;
        };
    }

    private void addConnectionGraph(final VersionedConnection connection, final GraphState edgeState,
            final GraphCollector collector) {
        if (connection == null || connection.getSource() == null || connection.getDestination() == null) {
            return;
        }

        addGraphNode(connection.getSource().getId(), null, connection.getSource(), classifyNodeState(connection.getSource().getId(), null), collector);
        addGraphNode(connection.getDestination().getId(), null, connection.getDestination(), classifyNodeState(connection.getDestination().getId(), null), collector);
        addGraphEdge(connection.getSource().getId(), connection.getDestination().getId(), relationshipLabel(connection), edgeState, collector);
    }

    private String relationshipLabel(final VersionedConnection connection) {
        if (connection == null || connection.getSelectedRelationships() == null
                || connection.getSelectedRelationships().isEmpty()) {
            return null;
        }
        return String.join(", ", connection.getSelectedRelationships());
    }

    private void addGraphEdge(final String sourceId, final String destinationId, final String label,
            final GraphState state, final GraphCollector collector) {
        if (sourceId == null || destinationId == null) {
            return;
        }
        collector.edges.add(new GraphEdge(sourceId, destinationId, label, state));
    }

    private void addGraphNode(final String componentId, final VersionedComponent versionedComponent,
            final ConnectableComponent connectableComponent, final GraphState requestedState,
            final GraphCollector collector) {
        if (componentId == null) {
            return;
        }
        final VersionedComponent resolvedVersionedComponent = versionedComponent != null ? versionedComponent : resolveVersionedComponent(componentId);
        final ConnectableComponent resolvedConnectable = connectableComponent != null ? connectableComponent : resolveConnectableComponent(componentId);
        final ComponentKind kind = resolveComponentKind(resolvedVersionedComponent, resolvedConnectable);
        String label = toMermaidLabel(resolveDisplayLabel(componentId, resolvedVersionedComponent, resolvedConnectable));
        if (kind == ComponentKind.PROCESSOR && resolvedVersionedComponent instanceof VersionedConfigurableExtension) {
            final String processorType = ((VersionedConfigurableExtension) resolvedVersionedComponent).getType();
            if (!isEmpty(processorType)) {
                label = label + "<br>#60;" + toMermaidLabel(substringAfterLast(processorType, ".")) + "#62;";
            }
        } else if (kind == ComponentKind.PORT) {
            final String portGroup = portGroupId(resolvedVersionedComponent, resolvedConnectable);
            if (portGroup != null && !portGroup.equals(collector.currentGroupId)) {
                final String portGroupName = resolveGroupName(portGroup);
                if (!isEmpty(portGroupName)) {
                    label = label + "<br>#91;" + toMermaidLabel(portGroupName) + "#93;";
                }
            }
        }
        final GraphState state = requestedState != null ? requestedState : classifyNodeState(componentId, resolvedVersionedComponent);
        final String nodeLabel = label;

        collector.nodes.compute(componentId, (id, existing) -> {
            if (existing == null) {
                return new GraphNode(id, "n" + collector.nodeSequence++, nodeLabel, kind, state);
            }
            existing.state = mergeGraphState(existing.state, state);
            return existing;
        });
    }

    private GraphState classifyNodeState(final String componentId, final VersionedComponent preferredComponent) {
        if (preferredComponent != null) {
            if (graphComponentsA.containsKey(componentId) && !graphComponentsB.containsKey(componentId)) {
                return GraphState.REMOVED;
            }
            if (!graphComponentsA.containsKey(componentId) && graphComponentsB.containsKey(componentId)) {
                return GraphState.ADDED;
            }
        }

        final boolean inA = graphComponentsA.containsKey(componentId) || connectableComponentsA.containsKey(componentId);
        final boolean inB = graphComponentsB.containsKey(componentId) || connectableComponentsB.containsKey(componentId);
        if (inA && !inB) {
            return GraphState.REMOVED;
        }
        if (!inA && inB) {
            return GraphState.ADDED;
        }
        return GraphState.CONTEXT;
    }

    private GraphState mergeGraphState(final GraphState current, final GraphState incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null || current == incoming) {
            return current;
        }
        if (current == GraphState.REMOVED || incoming == GraphState.REMOVED) {
            return GraphState.REMOVED;
        }
        if (current == GraphState.ADDED || incoming == GraphState.ADDED) {
            return GraphState.ADDED;
        }
        if (current == GraphState.MODIFIED || incoming == GraphState.MODIFIED) {
            return GraphState.MODIFIED;
        }
        return GraphState.CONTEXT;
    }

    private VersionedComponent resolveVersionedComponent(final String componentId) {
        final VersionedComponent componentB = graphComponentsB.get(componentId);
        if (componentB != null) {
            return componentB;
        }
        return graphComponentsA.get(componentId);
    }

    private ConnectableComponent resolveConnectableComponent(final String componentId) {
        final ConnectableComponent componentB = connectableComponentsB.get(componentId);
        if (componentB != null) {
            return componentB;
        }
        return connectableComponentsA.get(componentId);
    }

    private String resolveDisplayLabel(final String componentId, final VersionedComponent versionedComponent,
            final ConnectableComponent connectableComponent) {
        final String name;
        if (versionedComponent != null && !isEmpty(versionedComponent.getName())) {
            name = versionedComponent.getName();
        } else if (connectableComponent != null && !isEmpty(connectableComponent.getName())) {
            name = connectableComponent.getName();
        } else {
            name = null;
        }

        if (!isEmpty(name)) {
            return name;
        }

        return resolveComponentTypeLabel(versionedComponent, connectableComponent) + " " + shortId(componentId);
    }

    private String resolveComponentTypeLabel(final VersionedComponent versionedComponent,
            final ConnectableComponent connectableComponent) {
        if (versionedComponent != null && versionedComponent.getComponentType() != null) {
            return versionedComponent.getComponentType().getTypeName();
        }
        if (connectableComponent != null && connectableComponent.getType() != null) {
            return switch (connectableComponent.getType()) {
                case PROCESSOR -> "Processor";
                case INPUT_PORT -> "Input Port";
                case OUTPUT_PORT -> "Output Port";
                case REMOTE_INPUT_PORT -> "Remote Input Port";
                case REMOTE_OUTPUT_PORT -> "Remote Output Port";
                case FUNNEL -> "Funnel";
            };
        }
        return "Component";
    }

    private ComponentKind resolveComponentKind(final VersionedComponent versionedComponent,
            final ConnectableComponent connectableComponent) {
        if (versionedComponent != null && versionedComponent.getComponentType() != null) {
            return switch (versionedComponent.getComponentType()) {
                case PROCESSOR -> ComponentKind.PROCESSOR;
                case INPUT_PORT, OUTPUT_PORT -> ComponentKind.PORT;
                case FUNNEL -> ComponentKind.FUNNEL;
                case PROCESS_GROUP -> ComponentKind.GROUP;
                default -> ComponentKind.OTHER;
            };
        }
        if (connectableComponent != null && connectableComponent.getType() != null) {
            return switch (connectableComponent.getType()) {
                case PROCESSOR -> ComponentKind.PROCESSOR;
                case INPUT_PORT, OUTPUT_PORT, REMOTE_INPUT_PORT, REMOTE_OUTPUT_PORT -> ComponentKind.PORT;
                case FUNNEL -> ComponentKind.FUNNEL;
            };
        }
        return ComponentKind.OTHER;
    }

    private String kindNoun(final ComponentKind kind) {
        return switch (kind) {
            case PROCESSOR -> "processor";
            case PORT -> "port";
            case FUNNEL -> "funnel";
            case GROUP -> "group";
            case OTHER -> "component";
        };
    }

    private String shortId(final String componentId) {
        if (componentId == null) {
            return "unknown";
        }
        return componentId.length() <= 8 ? componentId : componentId.substring(0, 8);
    }

    private String portGroupId(final VersionedComponent versionedComponent, final ConnectableComponent connectableComponent) {
        if (versionedComponent != null && versionedComponent.getGroupIdentifier() != null) {
            return versionedComponent.getGroupIdentifier();
        }
        if (connectableComponent != null && connectableComponent.getGroupId() != null) {
            return connectableComponent.getGroupId();
        }
        return null;
    }

    private String resolveGroupName(final String groupId) {
        if (groupId == null) {
            return null;
        }
        final VersionedProcessGroup groupB = processGroupsB.get(groupId);
        if (groupB != null) {
            return groupB.getName();
        }
        final VersionedProcessGroup groupA = processGroupsA.get(groupId);
        return groupA == null ? null : groupA.getName();
    }

    private String toMermaidLabel(final String rawLabel) {
        final String label = rawLabel == null ? "Component" : rawLabel;
        return label
                .replace("#", "#35;")
                .replace("\"", "#quot;")
                .replace("|", "#124;")
                .replace("<", "#60;")
                .replace(">", "#62;")
                .replace("(", "#40;")
                .replace(")", "#41;")
                .replace("[", "#91;")
                .replace("]", "#93;")
                .replace("{", "#123;")
                .replace("}", "#125;")
                .replace("`", "#96;")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace("\r", "<br>");
    }

    private String renderGraph(final GraphCollector collector) {
        disambiguateLabels(collector);

        final StringBuilder builder = new StringBuilder();
        builder.append("flowchart TB\n");
        builder.append("classDef added fill:#d1fae5,stroke:#16a34a,color:#166534;\n");
        builder.append("classDef removed fill:#fee2e2,stroke:#dc2626,color:#991b1b,stroke-dasharray: 5 5;\n");
        builder.append("classDef modified fill:#fef3c7,stroke:#d97706,color:#92400e;\n");
        builder.append("classDef context fill:#e5e7eb,stroke:#6b7280,color:#374151;\n");

        for (final GraphNode node : collector.nodes.values()) {
            builder.append(node.mermaidId)
                    .append(openShape(node.kind))
                    .append('"')
                    .append(node.displayLabel)
                    .append('"')
                    .append(closeShape(node.kind))
                    .append(":::")
                    .append(toMermaidClass(node.state))
                    .append("\n");
        }

        int emittedEdge = 0;
        for (final GraphEdge edge : collector.edges) {
            final GraphNode sourceNode = collector.nodes.get(edge.sourceId);
            final GraphNode destinationNode = collector.nodes.get(edge.destinationId);
            if (sourceNode == null || destinationNode == null) {
                continue;
            }
            builder.append(sourceNode.mermaidId);
            if (!isEmpty(edge.label)) {
                builder.append(" -->|\"").append(toMermaidLabel(edge.label)).append("\"| ");
            } else {
                builder.append(" --> ");
            }
            builder.append(destinationNode.mermaidId).append("\n");
            builder.append("linkStyle ")
                    .append(emittedEdge)
                    .append(" ")
                    .append(toMermaidLinkStyle(edge.state))
                    .append("\n");
            emittedEdge++;
        }

        return builder.toString();
    }

    private void disambiguateLabels(final GraphCollector collector) {
        final Map<String, Integer> labelCounts = new HashMap<>();
        for (final GraphNode node : collector.nodes.values()) {
            labelCounts.merge(node.label, 1, Integer::sum);
        }
        for (final GraphNode node : collector.nodes.values()) {
            if (labelCounts.getOrDefault(node.label, 0) > 1) {
                node.displayLabel = node.label + " #40;" + kindNoun(node.kind) + "#41;";
            }
        }
    }

    private String openShape(final ComponentKind kind) {
        return switch (kind) {
            case PORT -> "([";
            case FUNNEL -> "((";
            case GROUP -> "[[";
            case PROCESSOR, OTHER -> "[";
        };
    }

    private String closeShape(final ComponentKind kind) {
        return switch (kind) {
            case PORT -> "])";
            case FUNNEL -> "))";
            case GROUP -> "]]";
            case PROCESSOR, OTHER -> "]";
        };
    }

    private String toMermaidClass(final GraphState state) {
        return switch (state) {
            case ADDED -> "added";
            case REMOVED -> "removed";
            case MODIFIED -> "modified";
            case CONTEXT -> "context";
        };
    }

    private String toMermaidLinkStyle(final GraphState state) {
        return switch (state) {
            case ADDED -> "stroke:#16a34a,stroke-width:2px;";
            case REMOVED -> "stroke:#dc2626,stroke-width:2px,stroke-dasharray: 5 5;";
            case MODIFIED -> "stroke:#d97706,stroke-width:2px;";
            case CONTEXT -> "stroke:#6b7280,stroke-width:1px;";
        };
    }

    private void registerGraphComponents(final VersionedProcessGroup group,
            final Map<String, VersionedComponent> components,
            final Map<String, ConnectableComponent> connectables) {
        registerGraphComponent(group, components);
        registerGraphComponents(group.getProcessors(), components);
        registerGraphComponents(group.getInputPorts(), components);
        registerGraphComponents(group.getOutputPorts(), components);
        registerGraphComponents(group.getFunnels(), components);
        registerConnectionEndpoints(group.getConnections(), connectables);

        final Set<VersionedProcessGroup> childGroups = group.getProcessGroups() != null ? group.getProcessGroups() : Set.of();
        for (final VersionedProcessGroup childGroup : childGroups) {
            registerGraphComponents(childGroup, components, connectables);
        }
    }

    private void registerGraphComponents(final Set<? extends VersionedComponent> componentsToRegister,
            final Map<String, VersionedComponent> components) {
        if (componentsToRegister == null) {
            return;
        }
        for (final VersionedComponent component : componentsToRegister) {
            registerGraphComponent(component, components);
        }
    }

    private void registerGraphComponent(final VersionedComponent component,
            final Map<String, VersionedComponent> components) {
        if (component != null && component.getIdentifier() != null) {
            components.put(component.getIdentifier(), component);
        }
    }

    private void registerConnectionEndpoints(final Set<VersionedConnection> connections,
            final Map<String, ConnectableComponent> connectables) {
        if (connections == null) {
            return;
        }
        for (final VersionedConnection connection : connections) {
            registerConnectable(connection.getSource(), connectables);
            registerConnectable(connection.getDestination(), connectables);
        }
    }

    private void registerConnectable(final ConnectableComponent component,
            final Map<String, ConnectableComponent> connectables) {
        if (component != null && component.getId() != null) {
            connectables.put(component.getId(), component);
        }
    }

    private void registerProcessGroups(final VersionedProcessGroup rootPG,
            final Map<String, VersionedProcessGroup> target) {
        final Set<VersionedProcessGroup> childPGs = rootPG.getProcessGroups() != null ? rootPG.getProcessGroups() : Set.of();
        for (final VersionedProcessGroup pg : childPGs) {
            target.put(pg.getIdentifier(), pg);
            registerProcessGroups(pg, target);
        }
    }

    private boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }

    private String substringAfterLast(final String str, final String separator) {
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