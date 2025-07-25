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
import java.util.List;

public class EmptyParameterRule implements CheckstyleRule {

    @Override
    public List<String> check(final FlowSnapshotContainer container, final String flowName, final RuleConfig config) {
        final List<String> violations = new ArrayList<>();
        final List<VersionedParameter> parameters = container.getFlowSnapshot()
                .getParameterContexts()
                .values()
                .stream()
                .flatMap(context -> context.getParameters().stream())
                .toList();

        for (VersionedParameter parameter : parameters) {
            if (parameter.getValue() != null && parameter.getValue().isEmpty()) {
                violations.add("Parameter named `" + parameter.getName() + "` is set to empty string");
            }
        }

        return violations;
    }

}
