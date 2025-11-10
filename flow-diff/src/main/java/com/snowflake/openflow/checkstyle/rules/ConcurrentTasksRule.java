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
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConcurrentTasksRule implements CheckstyleRule {

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final VersionedProcessGroup rootProcessGroup = container.getFlowSnapshot().getFlowContents();
        return checkConcurrentTasks(rootProcessGroup, config, flowName);
    }

    private List<String> checkConcurrentTasks(final VersionedProcessGroup processGroup, final RuleConfig ruleConfig, final String flowName) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(checkConcurrentTasks(childGroup, ruleConfig, flowName));
        }

        int limit = 2;

        if (ruleConfig != null && ruleConfig.parameters() != null && ruleConfig.parameters().get("limit") != null) {
            limit = ((Number) ruleConfig.parameters().get("limit")).intValue();
        }

        if (ruleConfig != null && ruleConfig.overrides() != null) {
            for (final Map.Entry<String, Map<String, Object>> entry : ruleConfig.overrides().entrySet()) {
                if (flowName != null && flowName.matches(entry.getKey())) {
                    final Object val = entry.getValue().get("limit");
                    if (val != null) {
                        limit = ((Number) val).intValue();
                    }
                }
            }
        }

        for (final VersionedProcessor processor : processGroup.getProcessors()) {
            final int concurrentTasks = processor.getConcurrentlySchedulableTaskCount();
            if (concurrentTasks > limit) {
                if (ruleConfig != null && ruleConfig.isComponentExcluded(flowName, processor.getIdentifier())) {
                    continue;
                }
                violations.add("Processor named `" + processor.getName() + "` (id: `" + processor.getIdentifier() + "`) is configured with " + concurrentTasks + " concurrent tasks");
            }
        }

        return violations;
    }

}
