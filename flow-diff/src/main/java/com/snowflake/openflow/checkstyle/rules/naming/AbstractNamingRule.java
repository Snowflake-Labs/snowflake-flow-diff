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

import com.snowflake.openflow.checkstyle.CheckstyleRule;
import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig.RuleConfig;

import java.util.Map;

public abstract class AbstractNamingRule implements CheckstyleRule {

    private static final String PATTERNS_KEY = "patterns";
    private static final String DEFAULT_PATTERN_KEY = "defaultPattern";

    protected Map<String, Object> getPatterns(final RuleConfig ruleConfig, final String flowName) {
        Map<String, Object> patterns = null;

        if (ruleConfig != null && ruleConfig.parameters() != null && ruleConfig.parameters().get(PATTERNS_KEY) != null) {
            patterns = (Map<String, Object>) ruleConfig.parameters().get(PATTERNS_KEY);
        }

        if (ruleConfig != null && ruleConfig.overrides() != null) {
            for (final Map.Entry<String, Map<String, Object>> entry : ruleConfig.overrides().entrySet()) {
                if (flowName != null && flowName.matches(entry.getKey())) {
                    final Object val = entry.getValue().get(PATTERNS_KEY);
                    if (val != null) {
                        patterns = (Map<String, Object>) val;
                    }
                }
            }
        }

        return patterns;
    }

    protected String getDefaultPattern(final RuleConfig ruleConfig, final String flowName) {
        String defaultPattern = null;

        if (ruleConfig != null && ruleConfig.parameters() != null && ruleConfig.parameters().get(DEFAULT_PATTERN_KEY) != null) {
            defaultPattern = ruleConfig.parameters().get(DEFAULT_PATTERN_KEY).toString();
        }

        if (ruleConfig != null && ruleConfig.overrides() != null) {
            for (final Map.Entry<String, Map<String, Object>> entry : ruleConfig.overrides().entrySet()) {
                if (flowName != null && flowName.matches(entry.getKey())) {
                    final Object val = entry.getValue().get(DEFAULT_PATTERN_KEY);
                    if (val != null) {
                        defaultPattern = val.toString();
                    }
                }
            }
        }

        return defaultPattern;
    }

    protected String resolvePattern(final String componentType, final Map<String, Object> patternsMap, final String defaultPattern) {
        if (patternsMap != null && patternsMap.containsKey(componentType)) {
            return patternsMap.get(componentType).toString();
        }
        return defaultPattern;
    }

    protected String shortType(final String fullyQualifiedType) {
        if (fullyQualifiedType == null || !fullyQualifiedType.contains(".")) {
            return fullyQualifiedType;
        }
        return fullyQualifiedType.substring(fullyQualifiedType.lastIndexOf('.') + 1);
    }
}
