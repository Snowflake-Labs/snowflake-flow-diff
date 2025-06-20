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
import org.apache.nifi.flow.VersionedControllerService;
import org.apache.nifi.flow.VersionedParameter;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.parameter.ExpressionLanguageAgnosticParameterParser;
import org.apache.nifi.parameter.ParameterParser;
import org.apache.nifi.parameter.ParameterReference;
import org.apache.nifi.parameter.ParameterTokenList;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnusedParameterRule implements CheckstyleRule {

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
            if (!isParameterReferenced(container, parameter.getName())) {
                violations.add("Parameter named `" + parameter.getName() + "` is not used anywhere in the flow");
            }
        }

        return violations;
    }

    private boolean isParameterReferenced(final FlowSnapshotContainer container, final String parameterName) {
        final ParameterParser parser = new ExpressionLanguageAgnosticParameterParser();
        final VersionedProcessGroup rootProcessGroup = container.getFlowSnapshot().getFlowContents();
        if (rootProcessGroup == null) {
            return false;
        }

        return isParameterReferenced(rootProcessGroup, parameterName, parser);
    }

    private boolean isParameterReferenced(final VersionedProcessGroup group, final String parameterName, final ParameterParser parser) {
        if (group.getProcessors() != null) {
            for (final VersionedProcessor processor : group.getProcessors()) {
                if (isParameterReferenced(processor.getProperties(), parameterName, parser)) {
                    return true;
                }
            }
        }

        if (group.getControllerServices() != null) {
            for (final VersionedControllerService service : group.getControllerServices()) {
                if (isParameterReferenced(service.getProperties(), parameterName, parser)) {
                    return true;
                }
            }
        }

        if (group.getProcessGroups() != null) {
            for (final VersionedProcessGroup child : group.getProcessGroups()) {
                if (isParameterReferenced(child, parameterName, parser)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isParameterReferenced(final Map<String, String> properties, final String parameterName, final ParameterParser parser) {
        if (properties == null) {
            return false;
        }

        for (final String value : properties.values()) {
            if (value == null) {
                continue;
            }

            final ParameterTokenList tokenList = parser.parseTokens(value);
            for (final ParameterReference reference : tokenList.toReferenceList()) {
                if (parameterName.equals(reference.getParameterName())) {
                    return true;
                }
            }
        }

        return false;
    }

}
