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
import org.apache.nifi.flow.VersionedParameterContext;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ParameterContextNamingRule extends AbstractNamingRule {

    private static final String EXCLUDE_KEY = "exclude";

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final String defaultPattern = getDefaultPattern(config, flowName);
        if (defaultPattern == null) {
            return List.of();
        }

        final List<String> excludeNames = getExcludeNames(config, flowName);
        final Map<String, VersionedParameterContext> parameterContexts = container.getFlowSnapshot().getParameterContexts();
        if (parameterContexts == null || parameterContexts.isEmpty()) {
            return List.of();
        }

        final List<String> violations = new ArrayList<>();

        for (final VersionedParameterContext context : parameterContexts.values()) {
            final String contextName = context.getName();
            if (excludeNames.contains(contextName)) {
                continue;
            }

            if (!contextName.matches(defaultPattern)) {
                violations.add("Parameter Context named `%s` does not match the expected naming pattern `%s`".formatted(contextName, defaultPattern));
            }
        }

        return violations;
    }

    private List<String> getExcludeNames(final RuleConfig ruleConfig, final String flowName) {
        List<String> excludeNames = Collections.emptyList();

        if (ruleConfig != null && ruleConfig.parameters() != null && ruleConfig.parameters().get(EXCLUDE_KEY) != null) {
            excludeNames = new ArrayList<>((List<String>) ruleConfig.parameters().get(EXCLUDE_KEY));
        }

        if (ruleConfig != null && ruleConfig.overrides() != null) {
            for (final Map.Entry<String, Map<String, Object>> entry : ruleConfig.overrides().entrySet()) {
                if (flowName != null && flowName.matches(entry.getKey())) {
                    final Object val = entry.getValue().get(EXCLUDE_KEY);
                    if (val != null) {
                        excludeNames = new ArrayList<>((List<String>) val);
                    }
                }
            }
        }

        return excludeNames;
    }
}
