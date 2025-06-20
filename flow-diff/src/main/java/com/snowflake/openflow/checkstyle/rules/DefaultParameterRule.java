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
import org.apache.nifi.flow.VersionedParameter;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultParameterRule implements CheckstyleRule {

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final List<String> violations = new ArrayList<>();
        final List<VersionedParameter> parameters = container.getFlowSnapshot()
                .getParameterContexts()
                .values()
                .stream()
                .flatMap(context -> context.getParameters().stream())
                .toList();

        List<String> parameterNamesWithDefaultValue = new ArrayList<String>();

        if (config != null && config.parameters() != null && config.parameters().get("defaultParameters") != null) {
            final String input = config.parameters().get("defaultParameters").toString();
            parameterNamesWithDefaultValue = Arrays.stream(input.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        if (config != null && config.overrides() != null) {
            for (final Map.Entry<String, Map<String, Object>> entry : config.overrides().entrySet()) {
                if (flowName != null && flowName.matches(entry.getKey())) {
                    final Object val = entry.getValue().get("defaultParameters");
                    if (val != null) {
                        final String input = val.toString();
                        parameterNamesWithDefaultValue.addAll(Arrays.stream(input.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList()));
                    }
                }
            }
        }

        for (VersionedParameter parameter : parameters) {
            if (parameterNamesWithDefaultValue.contains(parameter.getName()) && parameter.getValue() == null) {
                violations.add("Parameter named `" + parameter.getName() + "` is `null` even though it should have a default value");
            } else if (!parameterNamesWithDefaultValue.contains(parameter.getName()) && parameter.getValue() != null) {
                violations.add(
                        "Parameter named `" + parameter.getName() + "` is set with value `" + parameter.getValue() + "` and is not configured as a parameter that should have a default value");
            }
        }

        return violations;
    }

}
