package com.mythicrealm.api.gameplay.ai;

public interface AiDialogueProvider {
    ProviderCallResult generate(DialoguePrompt prompt);

    record DialoguePrompt(String systemText, String promptText) {
    }

    record ProviderCallResult(
        String provider,
        String model,
        String requestPayload,
        String rawResponse,
        String content,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String tokenSource,
        long latencyMs
    ) {
    }

    class ProviderException extends RuntimeException {
        private final String provider;
        private final String model;
        private final String requestPayload;
        private final String rawResponse;
        private final String errorType;
        private final long latencyMs;

        public ProviderException(
            String provider,
            String model,
            String requestPayload,
            String rawResponse,
            String errorType,
            String message,
            long latencyMs,
            Throwable cause
        ) {
            super(message, cause);
            this.provider = provider;
            this.model = model;
            this.requestPayload = requestPayload;
            this.rawResponse = rawResponse;
            this.errorType = errorType;
            this.latencyMs = latencyMs;
        }

        public String provider() {
            return provider;
        }

        public String model() {
            return model;
        }

        public String requestPayload() {
            return requestPayload;
        }

        public String rawResponse() {
            return rawResponse;
        }

        public String errorType() {
            return errorType;
        }

        public long latencyMs() {
            return latencyMs;
        }
    }
}
