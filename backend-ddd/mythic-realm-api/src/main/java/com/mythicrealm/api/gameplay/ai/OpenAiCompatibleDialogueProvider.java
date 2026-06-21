package com.mythicrealm.api.gameplay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleDialogueProvider implements AiDialogueProvider {
    private static final String PROVIDER = "openai-compatible";

    private final AiChatProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleDialogueProvider(AiChatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    }

    @Override
    public ProviderCallResult generate(DialoguePrompt prompt) {
        long start = System.nanoTime();
        String model = properties.openAiModel();
        if (properties.openAiBaseUrl().isBlank()) {
            throw providerException(model, "", "", "missing_base_url", "MaaS base-url 未配置", start, null);
        }
        if (properties.openAiApiKey().isBlank()) {
            throw providerException(model, "", "", "missing_api_key", "MaaS api-key 未配置", start, null);
        }
        if (model.isBlank()) {
            throw providerException(model, "", "", "missing_model", "MaaS model 未配置", start, null);
        }

        String url = endpointUrl();
        Map<String, Object> body = requestBody(model, prompt);
        String bodyJson = writeJson(body);
        String requestPayload = writeJson(Map.of(
            "method", "POST",
            "url", url,
            "body", body
        ));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + properties.openAiApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = elapsedMs(start);
            String rawResponse = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderException(
                    PROVIDER,
                    model,
                    requestPayload,
                    rawResponse,
                    "http_" + response.statusCode(),
                    "MaaS 返回 HTTP " + response.statusCode(),
                    latencyMs,
                    null
                );
            }
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode usage = root.path("usage");
            Integer promptTokens = nullableInt(usage.path("prompt_tokens"));
            Integer completionTokens = nullableInt(usage.path("completion_tokens"));
            Integer totalTokens = nullableInt(usage.path("total_tokens"));
            String tokenSource = "provider_reported";
            if (promptTokens == null && completionTokens == null && totalTokens == null) {
                promptTokens = estimateTokens(prompt.systemText() + "\n" + prompt.promptText());
                completionTokens = estimateTokens(content);
                totalTokens = promptTokens + completionTokens;
                tokenSource = "local_estimated";
            }
            return new ProviderCallResult(
                PROVIDER,
                model,
                requestPayload,
                rawResponse,
                content,
                promptTokens,
                completionTokens,
                totalTokens,
                tokenSource,
                latencyMs
            );
        } catch (ProviderException error) {
            throw error;
        } catch (Exception error) {
            throw providerException(model, requestPayload, "", error.getClass().getSimpleName(), error.getMessage(), start, error);
        }
    }

    private Map<String, Object> requestBody(String model, DialoguePrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
            Map.of("role", "system", "content", prompt.systemText()),
            Map.of("role", "user", "content", prompt.promptText())
        ));
        body.put("temperature", 0.82);
        body.put("max_tokens", 700);
        body.put("response_format", Map.of("type", "json_object"));
        return body;
    }

    private String endpointUrl() {
        String base = properties.openAiBaseUrl().replaceAll("/+$", "");
        String path = properties.completionsPath().startsWith("/") ? properties.completionsPath() : "/" + properties.completionsPath();
        return base + path;
    }

    private Integer nullableInt(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, (value.length() + 1) / 2);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{\"serializationError\":\"" + error.getClass().getSimpleName() + "\"}";
        }
    }

    private ProviderException providerException(
        String model,
        String requestPayload,
        String rawResponse,
        String errorType,
        String message,
        long startNanos,
        Throwable cause
    ) {
        return new ProviderException(
            PROVIDER,
            model,
            requestPayload,
            rawResponse,
            errorType,
            message == null || message.isBlank() ? "MaaS 调用失败" : message,
            elapsedMs(startNanos),
            cause
        );
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0, Duration.between(Instant.EPOCH, Instant.EPOCH.plusNanos(System.nanoTime() - startNanos)).toMillis());
    }
}
