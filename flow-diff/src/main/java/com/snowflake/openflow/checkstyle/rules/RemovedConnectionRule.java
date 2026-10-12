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
package com.snowflake.openflow.checkstyle.rules;

import com.snowflake.openflow.checkstyle.CheckstyleRule;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig.RuleConfig;
import org.apache.nifi.flow.VersionedConnection;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemovedConnectionRule implements CheckstyleRule {

    @Override
    public List<String> check(final FlowSnapshotContainer previousFlowSnapshotContainer,
            final FlowSnapshotContainer currentFlowSnapshotContainer,
            final String flowName,
            final RuleConfig config) {
        if (previousFlowSnapshotContainer == null) {
            return List.of();
        }

        final Set<String> currentConnectionIdentifiers = new HashSet<>();
        collectConnectionIdentifiers(currentFlowSnapshotContainer.getFlowSnapshot().getFlowContents(), currentConnectionIdentifiers);

        return findRemovedConnections(previousFlowSnapshotContainer.getFlowSnapshot().getFlowContents(), currentConnectionIdentifiers, flowName, config);
    }

    private void collectConnectionIdentifiers(final VersionedProcessGroup processGroup, final Set<String> connectionIdentifiers) {
        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            collectConnectionIdentifiers(childGroup, connectionIdentifiers);
        }

        for (final VersionedConnection connection : processGroup.getConnections()) {
            connectionIdentifiers.add(connection.getIdentifier());
        }
    }

    private List<String> findRemovedConnections(final VersionedProcessGroup processGroup,
            final Set<String> currentConnectionIdentifiers,
            final String flowName,
            final RuleConfig config) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(findRemovedConnections(childGroup, currentConnectionIdentifiers, flowName, config));
        }

        for (final VersionedConnection previousConnection : processGroup.getConnections()) {
            if (currentConnectionIdentifiers.contains(previousConnection.getIdentifier())) {
                continue;
            }

            if (config != null && config.isComponentExcluded(flowName, previousConnection.getIdentifier())) {
                continue;
            }

            violations.add("The connection `" + (isEmpty(previousConnection.getName())
                    ? previousConnection.getSelectedRelationships() == null ? "[]" : previousConnection.getSelectedRelationships().toString()
                    : previousConnection.getName()) + "` from `" + previousConnection.getSource().getName()
                    + "` to `" + previousConnection.getDestination().getName() + "` (id: `"
                    + previousConnection.getIdentifier() + "`) was removed from the previous flow version.");
        }

        return violations;
    }

    private boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }

}