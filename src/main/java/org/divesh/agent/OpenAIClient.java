package org.divesh.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenAIClient {

    private static final String API_URL =
            "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT =
            "You are a Git command assistant for a custom Git " +
                    "implementation called mygit.\n" +
                    "Convert natural language to mygit commands.\n\n" +
                    "Available commands:\n" +
                    "    mygit init\n" +
                    "    mygit add <file>\n" +
                    "    mygit add .\n" +
                    "    mygit commit -m \"message\"\n" +
                    "    mygit status\n" +
                    "    mygit log\n" +
                    "    mygit log --oneline\n" +
                    "    mygit branch\n" +
                    "    mygit branch <name>\n" +
                    "    mygit branch -d <name>\n" +
                    "    mygit checkout <branch>\n" +
                    "    mygit checkout -b <branch>\n" +
                    "    mygit diff\n" +
                    "    mygit diff --cached\n" +
                    "    mygit diff --HEAD\n" +
                    "    mygit diff <branch1>..<branch2>\n" +
                    "    mygit merge <branch>\n" +
                    "    mygit restore --staged <file>\n" +
                    "    mygit reset --soft HEAD~1\n" +
                    "    mygit reset --mixed HEAD~1\n" +
                    "    mygit reset --hard HEAD~1\n" +
                    "    mygit reset --hard HEAD\n" +
                    "    mygit stash\n" +
                    "    mygit stash pop\n" +
                    "    mygit stash list\n\n" +
                    "Respond in EXACTLY this format, two lines only:\n" +
                    "COMMAND: <the mygit command>\n" +
                    "EXPLAIN: <one line explanation, max 10 words>\n\n" +
                    "If you cannot translate:\n" +
                    "COMMAND: UNKNOWN\n" +
                    "EXPLAIN: could not understand the request";

    private final String apiKey;
    private final HttpClient httpClient;

    public OpenAIClient(String apiKey) {
        this.apiKey     = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AgentResponse translate(String userInput) throws Exception {
        String body = "{"
                + "\"model\": \"gpt-4o-mini\","
                + "\"max_tokens\": 150,"
                + "\"messages\": ["
                + "  {\"role\": \"system\", \"content\": "
                +    toJsonString(SYSTEM_PROMPT) + "},"
                + "  {\"role\": \"user\", \"content\": "
                +    toJsonString(userInput) + "}"
                + "]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type",  "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("API error: "
                    + response.statusCode()
                    + " " + response.body());
        }

        return parseResponse(response.body());
    }

    private AgentResponse parseResponse(String json) {
        // extract content value from JSON
        String content = extractContent(json);

        String command     = "UNKNOWN";
        String explanation = "could not understand the request";

        String[] lines = content.split("\n|\\\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("COMMAND:")) {
                command = trimmed.substring(8).trim();
            } else if (trimmed.startsWith("EXPLAIN:")) {
                explanation = trimmed.substring(8).trim();
            }
        }

        boolean dangerous = isDangerous(command);
        return new AgentResponse(command, explanation, dangerous);
    }

    private String extractContent(String json) {
        // try both with and without space after colon
        String[] markers = {
                "\"content\": \"",   // with space
                "\"content\":\""     // without space
        };

        for (String marker : markers) {
            int start = json.indexOf(marker);
            if (start == -1) continue;

            start += marker.length();
            // find closing quote — but skip escaped quotes
            int end = start;
            while (end < json.length()) {
                if (json.charAt(end) == '\\') {
                    end += 2; // skip escaped character
                    continue;
                }
                if (json.charAt(end) == '"') break;
                end++;
            }

            if (end > start) {
                return json.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .trim();
            }
        }
        return "";
    }

    private boolean isDangerous(String command) {
        return command.contains("--hard")
                || command.contains("--mixed")
                || command.contains("branch -d")
                || command.contains("branch --delete");
    }

    private String toJsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}