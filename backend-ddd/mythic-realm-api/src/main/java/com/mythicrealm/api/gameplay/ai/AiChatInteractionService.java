package com.mythicrealm.api.gameplay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import jakarta.annotation.PreDestroy;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AiChatInteractionService {
    private static final String PROMPT_VERSION = "ai-chat-v1";
    private static final Set<String> PLAYER_TRIGGERS = Set.of("player_message");
    private static final List<String> FORBIDDEN_TERMS = List.of(
        "ai", "model", "provider", "prompt", "backend", "database", "token",
        "大模型", "模型", "提示词", "系统提示", "后端", "数据库", "接口", "令牌"
    );
    private static final List<String> FORBIDDEN_ACTIONS = List.of(
        "我给你发", "我送你", "奖励已经", "给你金币", "给你装备", "帮你买",
        "替你强化", "我来给你刷", "已经到账", "发奖励"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiChatProperties properties;
    private final AiDialogueProvider dialogueProvider;
    private final TemplateDialogueProvider templateDialogueProvider;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64),
        runnable -> {
            Thread thread = new Thread(runnable, "ai-chat-worker");
            thread.setDaemon(true);
            return thread;
        },
        new ThreadPoolExecutor.DiscardPolicy()
    );

    public AiChatInteractionService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        AiChatProperties properties,
        OpenAiCompatibleDialogueProvider dialogueProvider,
        TemplateDialogueProvider templateDialogueProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.dialogueProvider = dialogueProvider;
        this.templateDialogueProvider = templateDialogueProvider;
    }

    public void enqueuePlayerMessage(PlayerRecord player, long messageId, String scope, String channel, String text) {
        long interactionId = createInteraction(
            scope,
            channel,
            "player_message",
            text,
            messageId,
            player.id(),
            player.name()
        );
        submit(interactionId);
    }

    public void enqueueSocialEvent(String scope, String channel, String triggerKind, String text, Long playerId, String playerName) {
        long interactionId = createInteraction(scope, channel, triggerKind, text, null, playerId, playerName);
        submit(interactionId);
    }

    @Scheduled(fixedDelay = 5000)
    public void sweepPendingInteractions() {
        List<Long> ids = jdbcTemplate.query(
            """
            SELECT id
            FROM ai_chat_interaction
            WHERE status = 'pending' AND scheduled_at <= CURRENT_TIMESTAMP
            ORDER BY id ASC
            LIMIT 8
            """,
            (rs, rowNum) -> rs.getLong("id")
        );
        ids.forEach(this::submit);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private long createInteraction(
        String scope,
        String channel,
        String triggerKind,
        String text,
        Long triggerMessageId,
        Long playerId,
        String playerName
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO ai_chat_interaction
                  (scope, channel, trigger_kind, trigger_text, trigger_message_id, player_id, player_name, status, mode)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, scope);
            statement.setString(2, channel);
            statement.setString(3, triggerKind);
            statement.setString(4, truncate(cleanText(text), 500));
            if (triggerMessageId == null) {
                statement.setObject(5, null);
            } else {
                statement.setLong(5, triggerMessageId);
            }
            if (playerId == null) {
                statement.setObject(6, null);
            } else {
                statement.setLong(6, playerId);
            }
            statement.setString(7, playerName);
            statement.setString(8, currentMode());
            return statement;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0 : keyHolder.getKey().longValue();
    }

    private void submit(long interactionId) {
        if (interactionId <= 0) {
            return;
        }
        try {
            executor.execute(() -> processInteraction(interactionId));
        } catch (RejectedExecutionException ignored) {
            // The scheduled sweeper will pick it up again.
        }
    }

    private void processInteraction(long interactionId) {
        int claimed = jdbcTemplate.update(
            """
            UPDATE ai_chat_interaction
            SET status = 'running', mode = ?, started_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'pending'
            """,
            currentMode(),
            interactionId
        );
        if (claimed == 0) {
            return;
        }
        Interaction interaction = loadInteraction(interactionId);
        if (interaction == null) {
            return;
        }
        List<RobotCandidate> candidates = chooseCandidates(interaction);
        String candidateIds = candidates.stream().map(candidate -> Long.toString(candidate.id())).collect(Collectors.joining(","));
        jdbcTemplate.update("UPDATE ai_chat_interaction SET candidate_robot_ids = ? WHERE id = ?", candidateIds, interactionId);

        try {
            if (!properties.enabled()) {
                handleDisabled(interaction, candidates);
                return;
            }
            if (properties.dryRun()) {
                handleDryRun(interaction, candidates);
                return;
            }
            if (isRateLimited(interaction.channel())) {
                handleSkippedOrFallback(interaction, candidates, "rate_limited", "AI 调用限频");
                return;
            }
            handleLive(interaction, candidates);
        } catch (Exception error) {
            handleUnexpectedFailure(interaction, candidates, error);
        }
    }

    private void handleDisabled(Interaction interaction, List<RobotCandidate> candidates) {
        if (isPlayerTrigger(interaction) && !candidates.isEmpty()) {
            int replies = insertTemplateFallback(interaction, candidates, "disabled_fallback");
            finish(interaction.id(), "completed", "disabled", true, replies, selectedIds(candidates, replies), null);
        } else {
            finish(interaction.id(), "skipped", "disabled", false, 0, "", null);
        }
    }

    private void handleDryRun(Interaction interaction, List<RobotCandidate> candidates) {
        PromptBundle prompt = buildPrompt(interaction, candidates);
        insertModelCall(
            interaction.id(),
            "dry-run",
            "",
            "dry_run",
            writeJson(Map.of("dryRun", true, "candidates", candidateIds(candidates))),
            prompt.promptText(),
            "",
            "",
            "",
            estimateTokens(prompt.systemText() + "\n" + prompt.promptText()),
            0,
            estimateTokens(prompt.systemText() + "\n" + prompt.promptText()),
            "local_estimated",
            0,
            true,
            null,
            null
        );
        finish(interaction.id(), "completed", "dry_run", false, 0, "", null);
    }

    private void handleLive(Interaction interaction, List<RobotCandidate> candidates) {
        if (candidates.isEmpty()) {
            finish(interaction.id(), "skipped", "live", false, 0, "", "没有可用候选机器人");
            return;
        }
        PromptBundle prompt = buildPrompt(interaction, candidates);
        AiDialogueProvider.ProviderCallResult callResult;
        try {
            callResult = dialogueProvider.generate(new AiDialogueProvider.DialoguePrompt(prompt.systemText(), prompt.promptText()));
        } catch (AiDialogueProvider.ProviderException error) {
            insertModelCall(
                interaction.id(),
                error.provider(),
                error.model(),
                "live",
                error.requestPayload(),
                prompt.promptText(),
                error.rawResponse(),
                "",
                "",
                null,
                null,
                null,
                "missing",
                error.latencyMs(),
                false,
                error.errorType(),
                truncate(error.getMessage(), 1000)
            );
            handleSkippedOrFallback(interaction, candidates, "provider_error", truncate(error.getMessage(), 1000));
            return;
        }

        ValidationResult validation = validateModelResponse(callResult.content(), candidates);
        int replyCount = 0;
        if (validation.valid()) {
            for (ValidatedReply reply : validation.replies()) {
                insertReply(
                    reply.robot(),
                    reply.text(),
                    interaction.channel(),
                    interaction.id(),
                    interaction.triggerMessageId(),
                    interaction.triggerKind(),
                    reply.delayMs()
                );
                if (interaction.playerId() != null) {
                    updateRelationshipMemory(reply.robot().id(), interaction.playerId(), 1, 0, 0, 1, reply.text(), "");
                }
                replyCount++;
            }
            applyRelationshipUpdates(interaction, candidates, validation.root());
            applyPublicMemories(interaction, validation.root());
        }
        insertModelCall(
            interaction.id(),
            callResult.provider(),
            callResult.model(),
            "live",
            callResult.requestPayload(),
            prompt.promptText(),
            callResult.rawResponse(),
            validation.parsedJson(),
            String.join("\n", validation.errors()),
            callResult.promptTokens(),
            callResult.completionTokens(),
            callResult.totalTokens(),
            callResult.tokenSource(),
            callResult.latencyMs(),
            validation.valid(),
            validation.valid() ? null : "validation_error",
            validation.valid() ? null : truncate(String.join("; ", validation.errors()), 1000)
        );
        if (!validation.valid()) {
            handleSkippedOrFallback(interaction, candidates, "validation_error", truncate(String.join("; ", validation.errors()), 1000));
            return;
        }
        String selectedIds = validation.replies().stream()
            .map(reply -> Long.toString(reply.robot().id()))
            .collect(Collectors.joining(","));
        finish(interaction.id(), "completed", "live", false, replyCount, selectedIds, null);
    }

    private void handleSkippedOrFallback(Interaction interaction, List<RobotCandidate> candidates, String status, String message) {
        if (isPlayerTrigger(interaction) && !candidates.isEmpty()) {
            int replies = insertTemplateFallback(interaction, candidates, status + "_fallback");
            finish(interaction.id(), "completed", currentMode(), true, replies, selectedIds(candidates, replies), message);
        } else {
            finish(interaction.id(), "skipped", currentMode(), false, 0, "", message);
        }
    }

    private void handleUnexpectedFailure(Interaction interaction, List<RobotCandidate> candidates, Exception error) {
        String message = truncate(error.getMessage(), 1000);
        handleSkippedOrFallback(interaction, candidates, "unexpected_error", message);
    }

    private int insertTemplateFallback(Interaction interaction, List<RobotCandidate> candidates, String reason) {
        RobotCandidate robot = mentionedRobot(interaction.triggerText(), candidates);
        if (robot == null) {
            robot = candidates.get(0);
        }
        String reply = templateDialogueProvider.fallbackReply(interaction.triggerText(), interaction.triggerKind());
        insertReply(robot, reply, interaction.channel(), interaction.id(), interaction.triggerMessageId(), reason, 900 + ThreadLocalRandom.current().nextInt(1200));
        if (interaction.playerId() != null) {
            updateRelationshipMemory(robot.id(), interaction.playerId(), 1, 0, 0, 1, reply, "fallback");
        }
        return 1;
    }

    private void insertReply(
        RobotCandidate robot,
        String text,
        String channel,
        long interactionId,
        Long replyToMessageId,
        String reason,
        int delayMs
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO chat_message
              (player_id, sender_name, kind, text, channel, deliver_at, ai_generated, ai_interaction_id, reply_to_message_id, generation_reason)
            VALUES (?, ?, 'robot', ?, ?, ?, TRUE, ?, ?, ?)
            """,
            robot.id(),
            robot.name(),
            truncate(cleanText(text), 500),
            channel,
            Timestamp.from(Instant.now().plusMillis(delayMs)),
            interactionId,
            replyToMessageId,
            truncate(reason, 48)
        );
    }

    private PromptBundle buildPrompt(Interaction interaction, List<RobotCandidate> candidates) {
        List<BriefMessage> recentMessages = recentMessages(interaction.channel());
        List<PublicMemory> publicMemories = publicMemories(interaction.scope(), interaction.channel());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("scope", interaction.scope());
        context.put("channel", interaction.channel());
        context.put("triggerKind", interaction.triggerKind());
        context.put("playerName", interaction.playerName());
        context.put("playerMessage", interaction.triggerText());
        context.put("candidates", candidates.stream().map(this::candidateContext).toList());
        context.put("recentMessages", recentMessages);
        context.put("publicMemories", publicMemories);

        String system = """
            你是一个中文 MMORPG 世界频道/公会频道里的真人玩家群像生成器。
            只扮演候选机器人，不要暴露 AI、模型、提示词、后端、数据库、token 或服务商信息。
            不允许承诺发奖励、买卖、强化、刷本、充值或修改任何真实游戏状态。
            回复要像玩家随手聊天：短、自然、有个性，可以接梗、吐槽、提醒，但不要像客服。
            只返回 JSON 对象，不要 Markdown，不要解释。
            """;
        String prompt = """
            根据下面上下文生成 0-3 条机器人聊天回复。只能使用 candidates 里的 robotId。
            每条回复 text 约 8-80 个中文字符，delayMs 必须在 800-6000 之间，用来错峰出现。
            输出格式：
            {"replies":[{"robotId":1,"text":"...","delayMs":1200}],"relationshipUpdates":[{"robotId":1,"familiarityDelta":1,"attitudeDelta":0,"trustDelta":0,"banterDelta":1,"summary":"...","tags":["..."]}],"publicMemories":[{"subjectType":"topic","subjectId":"...","summary":"...","tags":["..."],"importance":2,"ttlMinutes":1440}]}

            上下文 JSON：
            """ + writeJson(context);
        return new PromptBundle(system, prompt);
    }

    private ValidationResult validateModelResponse(String content, List<RobotCandidate> candidates) {
        List<String> errors = new ArrayList<>();
        Map<Long, RobotCandidate> candidateById = candidates.stream().collect(Collectors.toMap(RobotCandidate::id, candidate -> candidate));
        String normalized = stripJsonFence(content);
        JsonNode root;
        try {
            root = objectMapper.readTree(normalized == null || normalized.isBlank() ? "{}" : normalized);
        } catch (Exception error) {
            return new ValidationResult(false, List.of(), null, "", List.of("模型返回不是合法 JSON: " + error.getMessage()));
        }
        List<ValidatedReply> replies = new ArrayList<>();
        JsonNode replyNodes = root.path("replies");
        if (!replyNodes.isArray()) {
            errors.add("replies 必须是数组");
        } else if (replyNodes.size() > 3) {
            errors.add("回复数超过 3");
        } else {
            Set<Long> usedRobots = new HashSet<>();
            for (JsonNode node : replyNodes) {
                long robotId = node.path("robotId").asLong(0);
                RobotCandidate robot = candidateById.get(robotId);
                if (robot == null) {
                    errors.add("robotId 不在候选集: " + robotId);
                    continue;
                }
                if (!usedRobots.add(robotId)) {
                    errors.add("同一机器人重复回复: " + robotId);
                    continue;
                }
                String text = cleanText(node.path("text").asText(""));
                if (text.length() < 8 || text.length() > 90) {
                    errors.add("文本长度不合规: " + text);
                    continue;
                }
                if (containsForbiddenText(text)) {
                    errors.add("文本包含禁止内容: " + text);
                    continue;
                }
                int delayMs = node.path("delayMs").asInt(1200);
                if (delayMs < 800 || delayMs > 6000) {
                    errors.add("delayMs 不合规: " + delayMs);
                    continue;
                }
                replies.add(new ValidatedReply(robot, text, delayMs));
            }
        }
        String parsedJson = writeJson(root);
        return new ValidationResult(errors.isEmpty(), replies, root, parsedJson, errors);
    }

    private boolean containsForbiddenText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : FORBIDDEN_TERMS) {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String action : FORBIDDEN_ACTIONS) {
            if (text.contains(action)) {
                return true;
            }
        }
        return false;
    }

    private List<RobotCandidate> chooseCandidates(Interaction interaction) {
        List<RobotCandidate> pool = candidatePool(interaction);
        RobotCandidate mentioned = mentionedRobot(interaction.triggerText(), pool);
        return pool.stream()
            .sorted(Comparator.comparingDouble(candidate -> -scoreCandidate(candidate, mentioned, interaction.triggerText())))
            .limit(Math.min(8, Math.max(5, pool.size())))
            .toList();
    }

    private List<RobotCandidate> candidatePool(Interaction interaction) {
        if (interaction.channel().startsWith("guild:")) {
            Long guildId = parseGuildId(interaction.channel());
            if (guildId != null) {
                List<RobotCandidate> guildRobots = jdbcTemplate.query(
                    """
                    SELECT p.id, p.name, p.title, p.profession, p.level, p.personality, p.personality_archetype,
                           p.current_activity_text,
                           COALESCE(mem.familiarity, 0) AS familiarity,
                           COALESCE(mem.attitude, 0) AS attitude,
                           COALESCE(mem.trust, 0) AS trust,
                           COALESCE(mem.banter_level, 0) AS banter_level,
                           (SELECT MAX(cm.created_at) FROM chat_message cm WHERE cm.player_id = p.id AND cm.channel = ?) AS last_spoke_at
                    FROM guild_member gm
                    JOIN player p ON p.id = gm.player_id
                    LEFT JOIN ai_robot_relationship_memory mem ON mem.robot_id = p.id AND mem.player_id = ?
                    WHERE gm.guild_id = ? AND p.controller_type = 'robot'
                    ORDER BY RAND()
                    LIMIT 48
                    """,
                    (rs, rowNum) -> mapCandidate(rs),
                    interaction.channel(),
                    interaction.playerId() == null ? -1L : interaction.playerId(),
                    guildId
                );
                if (!guildRobots.isEmpty()) {
                    return guildRobots;
                }
            }
        }
        return jdbcTemplate.query(
            """
            SELECT p.id, p.name, p.title, p.profession, p.level, p.personality, p.personality_archetype,
                   p.current_activity_text,
                   COALESCE(mem.familiarity, 0) AS familiarity,
                   COALESCE(mem.attitude, 0) AS attitude,
                   COALESCE(mem.trust, 0) AS trust,
                   COALESCE(mem.banter_level, 0) AS banter_level,
                   (SELECT MAX(cm.created_at) FROM chat_message cm WHERE cm.player_id = p.id AND cm.channel = ?) AS last_spoke_at
            FROM player p
            LEFT JOIN ai_robot_relationship_memory mem ON mem.robot_id = p.id AND mem.player_id = ?
            WHERE p.controller_type = 'robot'
            ORDER BY RAND()
            LIMIT 48
            """,
            (rs, rowNum) -> mapCandidate(rs),
            interaction.channel(),
            interaction.playerId() == null ? -1L : interaction.playerId()
        );
    }

    private double scoreCandidate(RobotCandidate candidate, RobotCandidate mentioned, String triggerText) {
        double score = ThreadLocalRandom.current().nextDouble(0, 10);
        if (mentioned != null && mentioned.id() == candidate.id()) {
            score += 1000;
        }
        score += candidate.familiarity() * 0.6 + candidate.attitude() * 0.4 + candidate.trust() * 0.5 + candidate.banterLevel() * 0.3;
        if (triggerText != null && !candidate.currentActivityText().isBlank() && triggerText.contains("副本")
            && candidate.currentActivityText().contains("副本")) {
            score += 15;
        }
        if (candidate.lastSpokeAt() != null && candidate.lastSpokeAt().isAfter(Instant.now().minusSeconds(45))) {
            score -= 35;
        }
        return score;
    }

    private RobotCandidate mentionedRobot(String text, List<RobotCandidate> candidates) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (RobotCandidate candidate : candidates) {
            if (text.contains("@" + candidate.name()) || text.contains(candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

    private List<BriefMessage> recentMessages(String channel) {
        return jdbcTemplate.query(
            """
            SELECT sender_name, kind, text
            FROM chat_message
            WHERE channel = ? AND deliver_at <= CURRENT_TIMESTAMP
            ORDER BY id DESC
            LIMIT 12
            """,
            (rs, rowNum) -> new BriefMessage(rs.getString("sender_name"), rs.getString("kind"), rs.getString("text")),
            channel
        ).reversed();
    }

    private List<PublicMemory> publicMemories(String scope, String channel) {
        return jdbcTemplate.query(
            """
            SELECT subject_type, subject_id, summary, tags, importance
            FROM ai_public_memory
            WHERE scope = ? AND channel = ?
              AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            ORDER BY importance DESC, updated_at DESC
            LIMIT 6
            """,
            (rs, rowNum) -> new PublicMemory(
                rs.getString("subject_type"),
                rs.getString("subject_id"),
                rs.getString("summary"),
                rs.getString("tags"),
                rs.getInt("importance")
            ),
            scope,
            channel
        );
    }

    private void applyRelationshipUpdates(Interaction interaction, List<RobotCandidate> candidates, JsonNode root) {
        if (interaction.playerId() == null || root == null) {
            return;
        }
        Set<Long> candidateIds = candidates.stream().map(RobotCandidate::id).collect(Collectors.toSet());
        JsonNode updates = root.path("relationshipUpdates");
        if (!updates.isArray()) {
            return;
        }
        for (JsonNode update : updates) {
            long robotId = update.path("robotId").asLong(0);
            if (!candidateIds.contains(robotId)) {
                continue;
            }
            updateRelationshipMemory(
                robotId,
                interaction.playerId(),
                clamp(update.path("familiarityDelta").asInt(0), -3, 3),
                clamp(update.path("attitudeDelta").asInt(0), -3, 3),
                clamp(update.path("trustDelta").asInt(0), -3, 3),
                clamp(update.path("banterDelta").asInt(0), -3, 3),
                update.path("summary").asText(""),
                tags(update.path("tags"))
            );
        }
    }

    private void updateRelationshipMemory(
        long robotId,
        long playerId,
        int familiarityDelta,
        int attitudeDelta,
        int trustDelta,
        int banterDelta,
        String summary,
        String tags
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ai_robot_relationship_memory
              (robot_id, player_id, familiarity, attitude, trust, banter_level, summary, tags, last_interaction_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              familiarity = LEAST(100, GREATEST(0, familiarity + ?)),
              attitude = LEAST(100, GREATEST(-100, attitude + ?)),
              trust = LEAST(100, GREATEST(-100, trust + ?)),
              banter_level = LEAST(100, GREATEST(0, banter_level + ?)),
              summary = CASE WHEN VALUES(summary) = '' THEN summary ELSE VALUES(summary) END,
              tags = CASE WHEN VALUES(tags) = '' THEN tags ELSE VALUES(tags) END,
              last_interaction_at = CURRENT_TIMESTAMP
            """,
            robotId,
            playerId,
            Math.max(0, familiarityDelta),
            attitudeDelta,
            trustDelta,
            Math.max(0, banterDelta),
            truncate(cleanText(summary), 1000),
            truncate(tags, 500),
            familiarityDelta,
            attitudeDelta,
            trustDelta,
            banterDelta
        );
    }

    private void applyPublicMemories(Interaction interaction, JsonNode root) {
        if (root == null) {
            return;
        }
        JsonNode memories = root.path("publicMemories");
        if (!memories.isArray()) {
            return;
        }
        int inserted = 0;
        for (JsonNode memory : memories) {
            if (inserted >= 2) {
                return;
            }
            String summary = cleanText(memory.path("summary").asText(""));
            if (summary.length() < 8 || containsForbiddenText(summary)) {
                continue;
            }
            int ttlMinutes = clamp(memory.path("ttlMinutes").asInt(24 * 60), 60, 7 * 24 * 60);
            jdbcTemplate.update(
                """
                INSERT INTO ai_public_memory
                  (scope, channel, subject_type, subject_id, summary, tags, importance, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                interaction.scope(),
                interaction.channel(),
                truncate(cleanText(memory.path("subjectType").asText("topic")), 48),
                truncate(cleanText(memory.path("subjectId").asText("")), 96),
                truncate(summary, 1000),
                truncate(tags(memory.path("tags")), 500),
                clamp(memory.path("importance").asInt(1), 1, 5),
                Timestamp.from(Instant.now().plusSeconds(ttlMinutes * 60L))
            );
            inserted++;
        }
    }

    private boolean isRateLimited(String channel) {
        if (properties.maxCallsPerMinute() <= 0) {
            return true;
        }
        Long calls = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ai_model_call WHERE created_at >= ? AND mode = 'live'",
            Long.class,
            Timestamp.from(Instant.now().minusSeconds(60))
        );
        if (calls != null && calls >= properties.maxCallsPerMinute()) {
            return true;
        }
        if (properties.channelCooldownSeconds() <= 0) {
            return false;
        }
        Long recentChannelCalls = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM ai_chat_interaction
            WHERE channel = ? AND mode = 'live' AND status = 'completed' AND completed_at >= ?
            """,
            Long.class,
            channel,
            Timestamp.from(Instant.now().minusSeconds(properties.channelCooldownSeconds()))
        );
        return recentChannelCalls != null && recentChannelCalls > 0;
    }

    private void insertModelCall(
        long interactionId,
        String provider,
        String model,
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
        String errorMessage
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ai_model_call
              (interaction_id, provider, model, feature, prompt_version, mode, request_payload, prompt_text,
               raw_response, parsed_response, validation_errors, prompt_tokens, completion_tokens, total_tokens,
               token_source, latency_ms, success, error_type, error_message)
            VALUES (?, ?, ?, 'chat', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            interactionId,
            truncate(provider, 64),
            truncate(model, 128),
            PROMPT_VERSION,
            mode,
            requestPayload,
            promptText,
            rawResponse,
            parsedResponse,
            validationErrors,
            promptTokens,
            completionTokens,
            totalTokens,
            tokenSource == null || tokenSource.isBlank() ? "missing" : tokenSource,
            latencyMs,
            success,
            truncate(errorType, 64),
            truncate(errorMessage, 1000)
        );
    }

    private void finish(long interactionId, String status, String mode, boolean fallback, int replyCount, String selectedRobotIds, String errorMessage) {
        jdbcTemplate.update(
            """
            UPDATE ai_chat_interaction
            SET status = ?, mode = ?, fallback = ?, reply_count = ?, selected_robot_ids = ?,
                error_message = ?, completed_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            status,
            mode,
            fallback,
            replyCount,
            selectedRobotIds,
            truncate(errorMessage, 1000),
            interactionId
        );
    }

    private Interaction loadInteraction(long interactionId) {
        return jdbcTemplate.query(
            """
            SELECT id, scope, channel, trigger_kind, trigger_text, trigger_message_id, player_id, player_name
            FROM ai_chat_interaction
            WHERE id = ?
            """,
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                Long triggerMessageId = nullableLong(rs, "trigger_message_id");
                Long playerId = nullableLong(rs, "player_id");
                return new Interaction(
                    rs.getLong("id"),
                    rs.getString("scope"),
                    rs.getString("channel"),
                    rs.getString("trigger_kind"),
                    rs.getString("trigger_text"),
                    triggerMessageId,
                    playerId,
                    rs.getString("player_name")
                );
            },
            interactionId
        );
    }

    private RobotCandidate mapCandidate(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp lastSpokeAt = rs.getTimestamp("last_spoke_at");
        return new RobotCandidate(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("title"),
            rs.getString("profession"),
            rs.getInt("level"),
            rs.getString("personality"),
            rs.getString("personality_archetype"),
            rs.getString("current_activity_text"),
            rs.getInt("familiarity"),
            rs.getInt("attitude"),
            rs.getInt("trust"),
            rs.getInt("banter_level"),
            lastSpokeAt == null ? null : lastSpokeAt.toInstant()
        );
    }

    private Map<String, Object> candidateContext(RobotCandidate candidate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("robotId", candidate.id());
        value.put("name", candidate.name());
        value.put("title", candidate.title());
        value.put("profession", candidate.profession());
        value.put("level", candidate.level());
        value.put("personality", candidate.personality());
        value.put("activity", candidate.currentActivityText());
        value.put("familiarity", candidate.familiarity());
        value.put("attitude", candidate.attitude());
        value.put("trust", candidate.trust());
        value.put("banterLevel", candidate.banterLevel());
        return value;
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Long parseGuildId(String channel) {
        try {
            return Long.parseLong(channel.substring("guild:".length()));
        } catch (Exception error) {
            return null;
        }
    }

    private boolean isPlayerTrigger(Interaction interaction) {
        return PLAYER_TRIGGERS.contains(interaction.triggerKind());
    }

    private String currentMode() {
        if (!properties.enabled()) {
            return "disabled";
        }
        if (properties.dryRun()) {
            return "dry_run";
        }
        return "live";
    }

    private String selectedIds(List<RobotCandidate> candidates, int count) {
        return candidates.stream()
            .limit(count)
            .map(candidate -> Long.toString(candidate.id()))
            .collect(Collectors.joining(","));
    }

    private List<Long> candidateIds(List<RobotCandidate> candidates) {
        return candidates.stream().map(RobotCandidate::id).toList();
    }

    private String tags(JsonNode node) {
        if (!node.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode tag : node) {
            String value = cleanText(tag.asText(""));
            if (!value.isBlank()) {
                values.add(truncate(value, 32));
            }
            if (values.size() >= 8) {
                break;
            }
        }
        return String.join(",", values);
    }

    private String stripJsonFence(String content) {
        if (content == null) {
            return "";
        }
        String value = content.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                value = value.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{\"serializationError\":\"" + error.getClass().getSimpleName() + "\"}";
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(maxLength, value.length()));
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, (value.length() + 1) / 2);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Interaction(
        long id,
        String scope,
        String channel,
        String triggerKind,
        String triggerText,
        Long triggerMessageId,
        Long playerId,
        String playerName
    ) {
    }

    private record RobotCandidate(
        long id,
        String name,
        String title,
        String profession,
        int level,
        String personality,
        String personalityArchetype,
        String currentActivityText,
        int familiarity,
        int attitude,
        int trust,
        int banterLevel,
        Instant lastSpokeAt
    ) {
    }

    private record BriefMessage(String senderName, String kind, String text) {
    }

    private record PublicMemory(String subjectType, String subjectId, String summary, String tags, int importance) {
    }

    private record PromptBundle(String systemText, String promptText) {
    }

    private record ValidatedReply(RobotCandidate robot, String text, int delayMs) {
    }

    private record ValidationResult(boolean valid, List<ValidatedReply> replies, JsonNode root, String parsedJson, List<String> errors) {
    }
}
