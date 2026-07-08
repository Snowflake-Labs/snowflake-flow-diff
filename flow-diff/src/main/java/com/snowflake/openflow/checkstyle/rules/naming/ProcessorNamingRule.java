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
package com.snowflake.openflow.checkstyle.rules.naming;

import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig.RuleConfig;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProcessorNamingRule extends AbstractNamingRule {

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final VersionedProcessGroup rootProcessGroup = container.getFlowSnapshot().getFlowContents();
        return checkProcessorNaming(rootProcessGroup, config, flowName);
    }

    private List<String> checkProcessorNaming(final VersionedProcessGroup processGroup, final RuleConfig ruleConfig, final String flowName) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(checkProcessorNaming(childGroup, ruleConfig, flowName));
        }

        final Map<String, Object> patternsMap = getPatterns(ruleConfig, flowName);
        final String defaultPattern = getDefaultPattern(ruleConfig, flowName);

        if (patternsMap == null && defaultPattern == null) {
            return violations;
        }

        for (final VersionedProcessor processor : processGroup.getProcessors()) {
            if (ruleConfig != null && ruleConfig.isComponentExcluded(flowName, processor.getIdentifier())) {
                continue;
            }

            final String pattern = resolvePattern(processor.getType(), patternsMap, defaultPattern);

            if (pattern != null && !processor.getName().matches(pattern)) {
                violations.add("Processor of type `%s` named `%s` does not match the expected naming pattern `%s` (id: `%s`)".formatted(
                        shortType(processor.getType()), processor.getName(), pattern, processor.getIdentifier()));
            }
        }

        return violations;
    }
}
