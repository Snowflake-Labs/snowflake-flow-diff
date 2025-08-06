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
package com.snowflake.openflow.checkstyle;

import com.snowflake.openflow.checkstyle.CheckstyleRulesConfig.RuleConfig;
import com.snowflake.openflow.checkstyle.rules.ConcurrentTasksRule;
import com.snowflake.openflow.checkstyle.rules.DefaultParameterRule;
import com.snowflake.openflow.checkstyle.rules.EmptyParameterRule;
import com.snowflake.openflow.checkstyle.rules.EnforcePrioritizer;
import com.snowflake.openflow.checkstyle.rules.NoSelfLoopRule;
import com.snowflake.openflow.checkstyle.rules.SnapshotMetadataRule;
import com.snowflake.openflow.checkstyle.rules.UnusedParameterRule;

import java.util.List;

public enum DefaultCheckstyleRules {

    CONCURRENT_TASKS("concurrentTasks", new ConcurrentTasksRule()),
    SNAPSHOT_METADATA("snapshotMetadata", new SnapshotMetadataRule()),
    EMPTY_PARAMETER("emptyParameter", new EmptyParameterRule()),
    DEFAULT_PARAMETERS("defaultParameters", new DefaultParameterRule()),
    UNUSED_PARAMETER("unusedParameter", new UnusedParameterRule()),
    NO_SELF_LOOP("noSelfLoop", new NoSelfLoopRule()),
    ENFORCE_PRIORITIZER("enforcePrioritizer", new EnforcePrioritizer());

    private final String id;
    private final CheckstyleRule implementation;

    DefaultCheckstyleRules(String id, CheckstyleRule implementation) {
        this.id = id;
        this.implementation = implementation;
    }

    public String id() {
        return id;
    }

    public CheckstyleRule implementation() {
        return implementation;
    }

    public static DefaultCheckstyleRules fromId(String id) {
        for (DefaultCheckstyleRules rule : values()) {
            if (rule.id.equals(id)) {
                return rule;
            }
        }
        return null;
    }

    public boolean ruleApplies(final List<String> includes, final List<String> excludes, final CheckstyleRulesConfig config, final String flowName) {
        if (!includes.contains(id) || excludes.contains(id)) {
            return false;
        }

        if (config == null || config.rules() == null) {
            return true;
        }

        RuleConfig ruleConfig = config.rules().get(id);

        if (ruleConfig == null || ruleConfig.exclude() == null) {
            return true;
        }

        if (flowName == null) {
            return true;
        }

        return ruleConfig.exclude().stream().noneMatch(flowName::matches);
    }

}
