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
package com.snowflake.openflow;

import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig.RuleConfig;
import com.snowflake.openflow.checkstyle.DefaultCheckstyleRules;
import org.apache.nifi.registry.flow.FlowSnapshotContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlowCheckstyle {

    public static final List<String> DEFAULT_CHECKSTYLE_RULES = Arrays.stream(DefaultCheckstyleRules.values()).map(DefaultCheckstyleRules::id).toList();

    public static List<String> getCheckstyleViolations(final FlowSnapshotContainer flowSnapshotContainer, final String flowName, final CheckstyleRulesConfig config) {
        final List<String> violations = new ArrayList<>();
        final List<String> includes = config == null || config.include() == null ? DEFAULT_CHECKSTYLE_RULES : config.include();
        final List<String> excludes = config == null || config.exclude() == null || config.include() != null ? List.of() : config.exclude();

        Arrays.stream(DefaultCheckstyleRules.values())
                .filter(rule -> rule.ruleApplies(includes, excludes, config, flowName))
                .forEach(rule -> {
                    final RuleConfig ruleConfig = config == null || config.rules() == null ? null : config.rules().get(rule.id());
                    violations.addAll(rule.implementation().check(flowSnapshotContainer, flowName, ruleConfig));
                });

        return violations;
    }

}
