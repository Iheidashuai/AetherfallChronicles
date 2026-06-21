package com.mythicrealm.api.gameplay.ai;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiChatProperties {
    private final boolean enabled;
    private final boolean dryRun;
    private final int maxCallsPerMinute;
    private final int channelCooldownSeconds;
    private final String provider;
    private final String openAiBaseUrl;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String completionsPath;
    private final Duration requestTimeout;

    public AiChatProperties(
        @Value("${mythic.ai.chat.enabled:false}") boolean enabled,
        @Value("${mythic.ai.chat.dry-run:false}") boolean dryRun,
        @Value("${mythic.ai.chat.max-calls-per-minute:6}") int maxCallsPerMinute,
        @Value("${mythic.ai.chat.channel-cooldown-seconds:10}") int channelCooldownSeconds,
        @Value("${mythic.ai.provider:openai-compatible}") String provider,
        @Value("${spring.ai.openai.base-url:}") String openAiBaseUrl,
        @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
        @Value("${spring.ai.openai.chat.model:}") String openAiModel,
        @Value("${spring.ai.openai.chat.completions-path:/v1/chat/completions}") String completionsPath,
        @Value("${spring.ai.openai.chat.timeout-seconds:20}") int requestTimeoutSeconds
    ) {
        this.enabled = enabled;
        this.dryRun = dryRun;
        this.maxCallsPerMinute = Math.max(0, maxCallsPerMinute);
        this.channelCooldownSeconds = Math.max(0, channelCooldownSeconds);
        this.provider = provider == null || provider.isBlank() ? "openai-compatible" : provider.trim();
        this.openAiBaseUrl = trim(openAiBaseUrl);
        this.openAiApiKey = trim(openAiApiKey);
        this.openAiModel = trim(openAiModel);
        this.completionsPath = completionsPath == null || completionsPath.isBlank() ? "/v1/chat/completions" : completionsPath.trim();
        this.requestTimeout = Duration.ofSeconds(Math.max(3, requestTimeoutSeconds));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public int maxCallsPerMinute() {
        return maxCallsPerMinute;
    }

    public int channelCooldownSeconds() {
        return channelCooldownSeconds;
    }

    public String provider() {
        return provider;
    }

    public String openAiBaseUrl() {
        return openAiBaseUrl;
    }

    public String openAiApiKey() {
        return openAiApiKey;
    }

    public String openAiModel() {
        return openAiModel;
    }

    public String completionsPath() {
        return completionsPath;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
