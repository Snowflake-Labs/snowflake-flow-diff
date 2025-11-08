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
import org.apache.nifi.flow.VersionedConnection;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackpressureThresholdRule implements CheckstyleRule {

    // Captures the numeric portion at the start of NiFi backpressure size strings (e.g. "0 B").
    private static final Pattern LEADING_NUMBER_PATTERN = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)");

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final VersionedProcessGroup rootProcessGroup = container.getFlowSnapshot().getFlowContents();
        return checkProcessGroup(rootProcessGroup, config, flowName);
    }

    private List<String> checkProcessGroup(final VersionedProcessGroup processGroup,
                                           final RuleConfig ruleConfig,
                                           final String flowName) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(checkProcessGroup(childGroup, ruleConfig, flowName));
        }

        for (final VersionedConnection connection : processGroup.getConnections()) {
            evaluateConnection(connection, violations, ruleConfig, flowName);
        }

        return violations;
    }

    private void evaluateConnection(final VersionedConnection connection,
                                    final List<String> violations,
                                    final RuleConfig ruleConfig,
                                    final String flowName) {
        if (ruleConfig != null && ruleConfig.isComponentExcluded(flowName, connection.getIdentifier())) {
            return;
        }

        if (isZeroDataSizeThreshold(connection.getBackPressureDataSizeThreshold())) {
            violations.add("The connection " + describeConnection(connection) + " (id: `" + connection.getIdentifier()
                    + "`) has data size backpressure threshold set to 0. Configure a positive value to enable backpressure.");
        }

        if (isZeroObjectThreshold(connection.getBackPressureObjectThreshold())) {
            violations.add("The connection " + describeConnection(connection) + " (id: `" + connection.getIdentifier()
                    + "`) has object count backpressure threshold set to 0. Configure a positive value to enable backpressure.");
        }
    }

    private boolean isZeroDataSizeThreshold(final String threshold) {
        if (threshold == null) {
            return false;
        }

        final Matcher matcher = LEADING_NUMBER_PATTERN.matcher(threshold);
        if (!matcher.find()) {
            return false;
        }

        try {
            final double numericValue = Double.parseDouble(matcher.group(1));
            return Double.compare(numericValue, 0.0d) == 0;
        } catch (final NumberFormatException ex) {
            return false;
        }
    }

    private boolean isZeroObjectThreshold(final Long threshold) {
        return threshold != null && threshold == 0;
    }

    private String describeConnection(final VersionedConnection connection) {
        final String relationships = connection.getSelectedRelationships() == null ? "[]" : connection.getSelectedRelationships().toString();
        final String connectionName = isEmpty(connection.getName()) ? relationships : connection.getName();
        return "`" + connectionName + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName() + "`";
    }

    private boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }
}
