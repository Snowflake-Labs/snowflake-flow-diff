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
package com.snowflake.openflow.checkstyle.rules;

import com.snowflake.openflow.checkstyle.CheckstyleRule;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig.RuleConfig;
import org.apache.nifi.flow.ConnectableComponent;
import org.apache.nifi.flow.VersionedConnection;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.List;

public class NoSelfLoopRule implements CheckstyleRule {

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final VersionedProcessGroup rootProcessGroup = container.getFlowSnapshot().getFlowContents();
        return checkNoSelfLoop(rootProcessGroup, config, flowName);
    }

    private List<String> checkNoSelfLoop(final VersionedProcessGroup processGroup, final RuleConfig ruleConfig, final String flowName) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(checkNoSelfLoop(childGroup, ruleConfig, flowName));
        }

        for (final VersionedConnection connection : processGroup.getConnections()) {
            if (connection.getSource().getId().equals(connection.getDestination().getId())) {
                final ConnectableComponent component = connection.getSource();
                final String violation = String.format("Component named `%s` of type `%s` has a self-loop connection for relationship(s) `%s`. "
                        + "The recommended approach is to use the framework-level retry mechanism to avoid scenarios where FlowFiles would stay"
                        + " in the connection forever and to have proper backoff mechanism.",
                        component.getName(), component.getType().name(), connection.getSelectedRelationships().toString());
                violations.add(violation);
            }
        }

        return violations;
    }

}
