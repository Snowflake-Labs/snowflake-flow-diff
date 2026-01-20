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
package com.snowflake.openflow.github;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client for interacting with the GitHub API to manage PR comments.
 */
public class GitHubClient {

    // Text that uniquely identifies comments from this action (used to find and delete previous comments)
    private static final String ACTION_IDENTIFIER = "This GitHub Action is created and maintained by [Snowflake]";

    // GitHub Actions bot username
    private static final String GITHUB_ACTIONS_BOT = "github-actions[bot]";

    private final String token;
    private final String repository;
    private final String issueNumber;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubClient(String token, String repository, String issueNumber) {
        this.token = token;
        this.repository = repository;
        this.issueNumber = issueNumber;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Posts a new comment to the PR.
     *
     * @param body the comment body
     * @return true if the comment was posted successfully
     */
    public boolean postComment(String body) {
        try {
            final String apiUrl = "https://api.github.com/repos/" + repository + "/issues/" + issueNumber + "/comments";

            // Escape the body for JSON
            final String jsonBody = objectMapper.writeValueAsString(Map.of("body", body));

            final HttpRequest postRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Token " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            final HttpResponse<String> response = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.println("Successfully posted comment to PR");
                return true;
            } else {
                System.err.println("Failed to post comment: HTTP " + response.statusCode());
                System.err.println(response.body());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error posting comment: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes previous comments from this action on the PR (except the one just posted).
     * Only deletes comments that are both:
     * 1. Posted by github-actions[bot] (to never delete real user comments)
     * 2. Contain our action identifier text
     */
    public void deletePreviousComments() {
        try {
            // Collect all matching comment IDs across all pages
            final List<Long> matchingIds = new ArrayList<>();
            String nextUrl = "https://api.github.com/repos/" + repository + "/issues/" + issueNumber + "/comments?per_page=100";

            while (nextUrl != null) {
                final HttpRequest listRequest = HttpRequest.newBuilder()
                        .uri(URI.create(nextUrl))
                        .header("Authorization", "Token " + token)
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();

                final HttpResponse<String> response = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.err.println("Failed to list comments: HTTP " + response.statusCode());
                    break;
                }

                // Parse the JSON array to find comments with our identifier
                final List<Map<String, Object>> comments = objectMapper.readValue(response.body(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

                for (Map<String, Object> comment : comments) {
                    final String body = (String) comment.get("body");

                    // Check if comment is from github-actions[bot]
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> user = (Map<String, Object>) comment.get("user");
                    final String login = user != null ? (String) user.get("login") : null;
                    final boolean isFromBot = GITHUB_ACTIONS_BOT.equals(login);

                    // Only consider comments that are from the bot AND contain our identifier
                    if (isFromBot && body != null && body.contains(ACTION_IDENTIFIER)) {
                        matchingIds.add(((Number) comment.get("id")).longValue());
                    }
                }

                // Check for next page in Link header
                nextUrl = getNextPageUrl(response);
            }

            // Sort descending and skip the first one (newest - the one we just posted)
            matchingIds.sort(Collections.reverseOrder());
            if (matchingIds.size() > 1) {
                for (int i = 1; i < matchingIds.size(); i++) {
                    deleteComment(matchingIds.get(i));
                }
            }
        } catch (Exception e) {
            System.err.println("Error deleting previous comments: " + e.getMessage());
        }
    }

    /**
     * Parses the GitHub Link header to extract the "next" page URL.
     * Link header format: &lt;url&gt;; rel="next", &lt;url&gt;; rel="last"
     *
     * @param response the HTTP response containing the Link header
     * @return the next page URL, or null if there is no next page
     */
    private String getNextPageUrl(HttpResponse<String> response) {
        return response.headers().firstValue("Link")
                .flatMap(linkHeader -> {
                    // Parse Link header: <url>; rel="next", <url>; rel="last"
                    for (String part : linkHeader.split(",")) {
                        if (part.contains("rel=\"next\"")) {
                            // Extract URL between < and >
                            int start = part.indexOf('<');
                            int end = part.indexOf('>');
                            if (start >= 0 && end > start) {
                                return Optional.of(part.substring(start + 1, end));
                            }
                        }
                    }
                    return Optional.empty();
                })
                .orElse(null);
    }

    /**
     * Deletes a specific comment by ID.
     *
     * @param commentId the ID of the comment to delete
     */
    private void deleteComment(long commentId) {
        try {
            final String deleteUrl = "https://api.github.com/repos/" + repository + "/issues/comments/" + commentId;

            final HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .header("Authorization", "Token " + token)
                    .header("Accept", "application/vnd.github+json")
                    .DELETE()
                    .build();

            final HttpResponse<String> response = httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204) {
                System.out.println("Deleted previous comment ID: " + commentId);
            } else {
                System.err.println("Failed to delete comment " + commentId + ": HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Error deleting comment " + commentId + ": " + e.getMessage());
        }
    }
}
