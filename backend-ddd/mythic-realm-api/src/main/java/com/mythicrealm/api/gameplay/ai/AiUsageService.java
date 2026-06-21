package com.mythicrealm.api.gameplay.ai;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiUsageService {
    private final JdbcTemplate jdbcTemplate;

    public AiUsageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiUsageSummary summary() {
        AiTokenTotals totals = jdbcTemplate.query(
            """
            SELECT
              COALESCE(SUM(CASE WHEN DATE(created_at) = CURRENT_DATE THEN COALESCE(prompt_tokens, 0) ELSE 0 END), 0) AS today_prompt,
              COALESCE(SUM(CASE WHEN DATE(created_at) = CURRENT_DATE THEN COALESCE(completion_tokens, 0) ELSE 0 END), 0) AS today_completion,
              COALESCE(SUM(CASE WHEN DATE(created_at) = CURRENT_DATE THEN COALESCE(total_tokens, 0) ELSE 0 END), 0) AS today_total,
              COALESCE(SUM(COALESCE(prompt_tokens, 0)), 0) AS all_prompt,
              COALESCE(SUM(COALESCE(completion_tokens, 0)), 0) AS all_completion,
              COALESCE(SUM(COALESCE(total_tokens, 0)), 0) AS all_total,
              COUNT(*) AS total_calls,
              SUM(CASE WHEN success THEN 1 ELSE 0 END) AS success_calls,
              SUM(CASE WHEN success THEN 0 ELSE 1 END) AS failed_calls,
              COALESCE(AVG(NULLIF(latency_ms, 0)), 0) AS avg_latency
            FROM ai_model_call
            """,
            rs -> {
                if (!rs.next()) {
                    return new AiTokenTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                }
                return new AiTokenTotals(
                    rs.getLong("today_prompt"),
                    rs.getLong("today_completion"),
                    rs.getLong("today_total"),
                    rs.getLong("all_prompt"),
                    rs.getLong("all_completion"),
                    rs.getLong("all_total"),
                    rs.getLong("total_calls"),
                    rs.getLong("success_calls"),
                    rs.getLong("failed_calls"),
                    rs.getDouble("avg_latency")
                );
            }
        );
        LatestModel latest = latestModel();
        Long fallbackCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ai_chat_interaction WHERE fallback = TRUE",
            Long.class
        );
        double failureRate = totals.totalCalls() == 0 ? 0 : (double) totals.failedCalls() / totals.totalCalls();
        return new AiUsageSummary(
            totals.todayPromptTokens(),
            totals.todayCompletionTokens(),
            totals.todayTotalTokens(),
            totals.allPromptTokens(),
            totals.allCompletionTokens(),
            totals.allTotalTokens(),
            latest.provider(),
            latest.model(),
            Math.round(totals.avgLatencyMs()),
            failureRate,
            fallbackCount == null ? 0 : fallbackCount,
            totals.totalCalls(),
            totals.successCalls(),
            totals.failedCalls()
        );
    }

    public List<AiModelCallRow> calls(int limit) {
        int boundedLimit = Math.max(1, Math.min(500, limit));
        return jdbcTemplate.query(
            """
            SELECT id, interaction_id, provider, model, feature, prompt_version, mode,
                   prompt_tokens, completion_tokens, total_tokens, token_source,
                   latency_ms, success, error_type, error_message, created_at
            FROM ai_model_call
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new AiModelCallRow(
                rs.getLong("id"),
                nullableLong(rs, "interaction_id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("feature"),
                rs.getString("prompt_version"),
                rs.getString("mode"),
                nullableInt(rs, "prompt_tokens"),
                nullableInt(rs, "completion_tokens"),
                nullableInt(rs, "total_tokens"),
                rs.getString("token_source"),
                rs.getLong("latency_ms"),
                rs.getBoolean("success"),
                rs.getString("error_type"),
                rs.getString("error_message"),
                rs.getTimestamp("created_at").toInstant()
            ),
            boundedLimit
        );
    }

    public AiModelCallDetail call(long id) {
        return jdbcTemplate.query(
            """
            SELECT id, interaction_id, provider, model, feature, prompt_version, mode,
                   request_payload, prompt_text, raw_response, parsed_response, validation_errors,
                   prompt_tokens, completion_tokens, total_tokens, token_source,
                   latency_ms, success, error_type, error_message, created_at
            FROM ai_model_call
            WHERE id = ?
            """,
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new AiModelCallDetail(
                    rs.getLong("id"),
                    nullableLong(rs, "interaction_id"),
                    rs.getString("provider"),
                    rs.getString("model"),
                    rs.getString("feature"),
                    rs.getString("prompt_version"),
                    rs.getString("mode"),
                    rs.getString("request_payload"),
                    rs.getString("prompt_text"),
                    rs.getString("raw_response"),
                    rs.getString("parsed_response"),
                    rs.getString("validation_errors"),
                    nullableInt(rs, "prompt_tokens"),
                    nullableInt(rs, "completion_tokens"),
                    nullableInt(rs, "total_tokens"),
                    rs.getString("token_source"),
                    rs.getLong("latency_ms"),
                    rs.getBoolean("success"),
                    rs.getString("error_type"),
                    rs.getString("error_message"),
                    rs.getTimestamp("created_at").toInstant()
                );
            },
            id
        );
    }

    private LatestModel latestModel() {
        return jdbcTemplate.query(
            """
            SELECT provider, model
            FROM ai_model_call
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            rs -> rs.next() ? new LatestModel(rs.getString("provider"), rs.getString("model")) : new LatestModel("", "")
        );
    }

    private Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record AiTokenTotals(
        long todayPromptTokens,
        long todayCompletionTokens,
        long todayTotalTokens,
        long allPromptTokens,
        long allCompletionTokens,
        long allTotalTokens,
        long totalCalls,
        long successCalls,
        long failedCalls,
        double avgLatencyMs
    ) {
    }

    private record LatestModel(String provider, String model) {
    }

    public record AiUsageSummary(
        long todayPromptTokens,
        long todayCompletionTokens,
        long todayTotalTokens,
        long allPromptTokens,
        long allCompletionTokens,
        long allTotalTokens,
        String provider,
        String model,
        long avgLatencyMs,
        double failureRate,
        long fallbackCount,
        long totalCalls,
        long successCalls,
        long failedCalls
    ) {
    }

    public record AiModelCallRow(
        long id,
        Long interactionId,
        String provider,
        String model,
        String feature,
        String promptVersion,
        String mode,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String tokenSource,
        long latencyMs,
        boolean success,
        String errorType,
        String errorMessage,
        Instant createdAt
    ) {
    }

    public record AiModelCallDetail(
        long id,
        Long interactionId,
        String provider,
        String model,
        String feature,
        String promptVersion,
        String mode,
        String requestPayload,
        String promptText,
        String rawResponse,
        String parsedResponse,
        String validationErrors,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String tokenSource,
        long latencyMs,
        boolean success,
        String errorType,
        String errorMessage,
        Instant createdAt
    ) {
    }
}
