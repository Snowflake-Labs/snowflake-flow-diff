#!/bin/sh -l

# Copyright 2025 Snowflake Inc.
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

java -jar /flow-diff.jar "$1" "$2" "$6" "$7" "$8" >> /github/workspace/diff.txt
FLOW_EXIT_CODE=$?

OUTPUT=$(cat /github/workspace/diff.txt | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | sed 's/\t/    /g' | sed ':a;N;$!ba;s/\n/\\n/g')

curl -X POST \
     -H "Authorization: Token $3" \
     -H "Accept: application/vnd.github+json" \
     https://api.github.com/repos/$4/issues/$5/comments \
     -d "{\"body\":\"$OUTPUT\"}"
CURL_EXIT_CODE=$?

if [ $FLOW_EXIT_CODE -ne 0 ]; then
    exit $FLOW_EXIT_CODE
fi

exit $CURL_EXIT_CODE
