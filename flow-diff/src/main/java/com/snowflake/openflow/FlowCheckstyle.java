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

import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedProcessor;

import java.util.ArrayList;
import java.util.List;

public class FlowCheckstyle {

    public static List<String> getCheckstyleViolations(final VersionedProcessGroup flow) {
        final List<String> violations = new ArrayList<>();

        // check concurrent tasks
        violations.addAll(checkConcurrentTasks(flow));

        return violations;
    }

    private static List<String> checkConcurrentTasks(VersionedProcessGroup processGroup) {
        final List<String> violations = new ArrayList<>();

        for (final VersionedProcessGroup childGroup : processGroup.getProcessGroups()) {
            violations.addAll(checkConcurrentTasks(childGroup));
        }

        for (final VersionedProcessor processor : processGroup.getProcessors()) {
            final int concurrentTasks = processor.getConcurrentlySchedulableTaskCount();
            if (concurrentTasks > 2) {
                violations.add("Processor named `" + processor.getName() + "` is configured with " + concurrentTasks + " concurrent tasks");
            }
        }

        return violations;
    }

}
