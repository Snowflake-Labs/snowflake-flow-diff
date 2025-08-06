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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnforcePrioritizer implements CheckstyleRule {

    private final static String PARAMETER_NAME = "prioritizers";

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final VersionedProcessGroup rootProcessGroup = container.getFlowSnapshot().getFlowContents();

        String prioritizersString = null;

        if (config != null && config.parameters() != null && config.parameters().get(PARAMETER_NAME) != null) {
            prioritizersString = config.parameters().get(PARAMETER_NAME).toString();
        }

        if (config != null && config.overrides() != null) {
            for (final Map.Entry<String, Map<String, Object>> entry : config.overrides().entrySet()) {
                if (flowName != null && flowName.matches(entry.getKey())) {
                    final Object val = entry.getValue().get(PARAMETER_NAME);
                    if (val != null) {
                        prioritizersString = val.toString();
                    }
                }
            }
        }

        if (prioritizersString != null) {
            final List<String> prioritizers = Arrays.stream(prioritizersString.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
            return checkConnectionsPrioritizers(rootProcessGroup, prioritizers, flowName);
        } else {
            // no configuration set, so no violation
            return List.of();
        }
    }

    private List<String> checkConnectionsPrioritizers(final VersionedProcessGroup processGroup, final List<String> prioritizers, final String flowName) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(checkConnectionsPrioritizers(childGroup, prioritizers, flowName));
        }

        for (final VersionedConnection connection : processGroup.getConnections()) {
            final List<String> currentPrioritizers = connection.getPrioritizers();
            if (!sameContentsSorted(prioritizers, currentPrioritizers)) {
                violations.add("The connection `" + (isEmpty(connection.getName()) ? connection.getSelectedRelationships().toString() : connection.getName())
                        + "` from `" + connection.getSource().getName() + "` to `" + connection.getDestination().getName()
                        + " is configured with prioritizers `" + currentPrioritizers.toString() + "` but should have " + prioritizers.toString());
            }
        }

        return violations;
    }

    private boolean sameContentsSorted(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }

        List<String> copyA = new ArrayList<>(a);
        List<String> copyB = new ArrayList<>(b);

        Collections.sort(copyA);
        Collections.sort(copyB);

        return copyA.equals(copyB);
    }

    private boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }

}
