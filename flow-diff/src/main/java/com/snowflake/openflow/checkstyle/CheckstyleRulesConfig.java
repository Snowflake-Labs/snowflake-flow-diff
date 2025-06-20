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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public record CheckstyleRulesConfig(
        List<String> include,
        List<String> exclude,
        Map<String, RuleConfig> rules) {

    public static CheckstyleRulesConfig fromFile(String path) throws IOException {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(new File(path), CheckstyleRulesConfig.class);
        } catch (IOException e) {
            System.err.println("Failed to read Checkstyle rules configuration from file: " + path);
            e.printStackTrace();
            return null;
        }
    }

    public record RuleConfig(
            Map<String, Object> parameters,
            Map<String, Map<String, Object>> overrides,
            List<String> exclude) {
    }
}