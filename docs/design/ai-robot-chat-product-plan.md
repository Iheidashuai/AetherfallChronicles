# AI Robot Chat Product Plan

## Summary

Add an AI-assisted social layer to the existing robot population so the world and guild channels feel like they contain real same-server players.

The current robot system should remain responsible for real gameplay behavior: dungeon runs, enhancement, market activity, recharge simulation, builds, abyss/rift runs, guild actions, and progression. The large model should only generate social expression, conversational intent, and memory updates. It must not directly mutate gameplay state or decide economy/progression operations.

Target cost envelope: one good-feeling chat interaction should usually fit in 1k-3k tokens total.

## Product Goal

Make the game feel like a live MMO-style menu RPG:

- The player says something in world/guild chat and gets natural replies.
- Player achievements feel noticed by the server.
- Robots have recognizable personalities, preferences, and relationships.
- Different robots sometimes disagree, tease, give imperfect advice, or remember old topics.
- The game still works if AI is disabled, rate-limited, slow, or invalid.

## Non-Goals

First version does not include:

- Full private chat windows.
- AI companion / romance / deep single-character relationship routes.
- LLM-controlled gameplay actions.
- LLM access to hidden system internals, drop rates, decision scores, or full inventory state.
- Heavy production-grade moderation.
- In-game exposure of provider/model/token details to ordinary players.
- Prompt editing UI.
- Hot model switching from the game UI.

## Experience Principles

### Robots Speak Like Players

Robots should sound like same-server game players, not NPCs or customer support.

Good tone:

- "你这件装备别急着卖，词条还行。"
- "+7 以后我一般留一笔金币兜底。"
- "我刚也翻车了，别问，铁匠今天手黑。"

Bad tone:

- "勇士，你的命运正在召唤。"
- "根据系统规则，建议你强化装备。"
- "我是 AI 模型，正在分析你的数据。"

### Human-Like Timing

Do not reply instantly with multiple messages. Replies should be staggered:

- First reply: 0.8-2.0 seconds after trigger.
- Second reply: another 1.5-4.0 seconds later.
- Third reply: another 2.0-6.0 seconds later.

No typing indicator in version 1. Delayed messages should simply appear naturally.

### Limited Knowledge

Robots can know public and player-visible information:

- Public profile: name, class, level, power, title, leaderboard presence.
- Recent visible events: enhancement result, legendary loot, dungeon clear, market sale, arena move.
- Channel history and relationship memory.
- Their own real recent robot activity.

Robots must not know:

- Hidden drop rates.
- Internal action scores.
- Full player inventory unless explicitly exposed.
- Full state of all 200 robots.
- Backend/database implementation details.

### Imperfect But Not Misleading

Robots may have opinions, bias, disagreement, and light misjudgment:

- Merchant robots may overvalue market sniping.
- Hardcore robots may push risky progression.
- Cautious robots may advise farming and saving resources.
- Newbie-like robots may be corrected by others.

They must not invent nonexistent systems, items, rewards, bosses, or rules.

## Trigger Strategy

Version 1 is event-triggered and player-triggered. It is not high-frequency ambient chatter.

### Player Chat Triggers

- Player sends a world chat message.
- Player sends a guild chat message.
- Player uses `@robotName` in a channel.
- Player clicks a robot profile and uses a "ask in channel" style action.

Expected behavior:

- The player message is saved immediately.
- AI response generation happens asynchronously.
- 0-3 robot replies are written with staggered `deliver_at` times.
- `@robotName` gives the named robot highest response priority.

### High-Emotion Event Triggers

First version should cover only events that create "the server noticed me" feeling:

- Player enhancement reaches key levels: +7, +10, +12.
- Player has high-level enhancement failure or repeated failures.
- Player obtains legendary or immortal equipment.
- Player first-clears an important dungeon.
- Player breaks through a rift tier.
- Player significantly improves arena rank.
- Player sells a high-value market item.
- Player joins or creates a guild.
- Guild boss high contribution or kill event.

Ordinary events should not trigger AI:

- Normal dungeon runs.
- Normal equipment drops.
- Routine quest claims.
- Small gold changes.
- Inventory cleanup.
- Routine skill training.

### Ambient Chatter

Ambient AI chatter should be low-frequency and optional. If the model is unavailable, ambient chatter should be skipped rather than forced through awkward fallback text.

## Candidate Selection

Never send all 200 robots to the model.

Backend should first score and select 5-8 candidate robots, then ask the model to generate 0-3 replies from that candidate set.

Scoring inputs:

- Mention match: `@robotName` puts that robot at the top.
- Relationship: robots familiar with the player are more likely.
- Topic affinity: market robots for market topics, hardcore robots for dungeon/rift topics, social robots for casual talk.
- Current activity: recently active robots feel more online.
- Recent notable event: robots with something relevant to say are favored.
- Anti-repeat: recently speaking robots are downweighted.
- Group mix: prefer one useful reply, one flavor reply, and one optional follow-up over three identical advice messages.

## Candidate Context

Each candidate should be represented briefly:

```json
{
  "id": 17,
  "name": "洛缇娅",
  "title": "裂隙观测者",
  "profession": "mage",
  "level": 34,
  "power": 18200,
  "archetype": "SOCIAL",
  "personalityOneLine": "喜欢在频道里接话，常把深渊失败讲成经验。",
  "currentActivityKind": "rift",
  "currentActivityText": "刚打深渊 T4 失败，正在补淬炼材料。",
  "recentNotableEvent": "深渊 T4 失败，差一口输出。",
  "relationshipSummaryWithPlayer": "上次建议玩家先强化武器，玩家接受了建议。",
  "familiarity": 3,
  "attitude": 1,
  "speakingCooldown": false,
  "topicAffinity": ["rift", "progression", "mage-build"]
}
```

Do not include complete equipment lists, complete bags, long histories, formulas, or all server state.

## Model Call Shape

First version should use one model call per interaction and generate all replies in one structured response.

### Input Budget

Typical prompt should include:

- System/developer instruction: 200-400 tokens.
- Player and channel context: 100-250 tokens.
- Recent chat history, 8-12 messages: 400-1000 tokens.
- Candidate robots, 5-8 compact records: 600-1400 tokens.
- Relationship and public memories: 200-600 tokens.
- Output schema and constraints: 150-300 tokens.

Expected total:

- Normal interaction: 1.0k-2.3k tokens.
- Rich interaction with multiple candidates/memory: 1.5k-3.3k tokens.

### Required Structured Output

The model must return JSON. Backend validates before writing anything visible.

Example:

```json
{
  "replies": [
    {
      "robotId": 123,
      "text": "你这战力先别硬冲下一层，武器补到 +6 会稳很多。",
      "mood": "helpful",
      "topic": "progression_advice",
      "delayMs": 1400
    },
    {
      "robotId": 88,
      "text": "也可以赌一手，当然翻车别说是我教的。",
      "mood": "teasing",
      "topic": "risk_taking",
      "delayMs": 3800
    }
  ],
  "memoryUpdates": [
    {
      "scope": "robot_relationship",
      "robotId": 123,
      "playerId": 456,
      "summary": "玩家在卡副本时接受过稳健强化建议，倾向先补装备再推进。",
      "familiarityDelta": 1,
      "attitudeDelta": 0,
      "trustDelta": 1,
      "banterDelta": 0,
      "tags": ["progression", "warrior", "cautious"]
    }
  ],
  "publicMemoryUpdates": [
    {
      "scope": "world",
      "subjectType": "player",
      "subjectId": 456,
      "summary": "玩家在世界频道询问卡副本后的战力提升路线。",
      "importance": 3,
      "tags": ["progression", "advice"]
    }
  ]
}
```

### Validation Rules

Backend must validate:

- `replies.length` is 0-3.
- `robotId` belongs to the candidate set.
- `text` length is bounded, for example 8-80 Chinese characters.
- `delayMs` is bounded, for example 800-6000.
- Text does not mention being AI, model, provider, prompt, backend, database, token, or system internals.
- Text does not reference facts that were not provided in the prompt.
- Text does not promise rewards or mutate state.
- Memory updates are bounded, summarized, and tied to allowed scopes.
- Invalid response falls back or is skipped according to trigger type.

## Memory Model

Use a hybrid memory model:

- Public memory: what the channel/server can reasonably remember.
- Robot relationship memory: what a specific robot remembers about a specific player.

Avoid all robots sharing one synchronized mind.

### Public Memory

Purpose: allow robots to naturally reference shared visible events.

Store only high-value memories:

- First clears.
- Key enhancement events.
- Legendary/immortal loot.
- Big arena/rift breakthroughs.
- High-value market sales.
- Repeated player topics in public chat.
- Major robot events that were visible in chat/activity.

Suggested table: `ai_public_memory`

Fields:

- `id`
- `scope`: `world` or `guild`
- `subject_type`: `player`, `robot`, `market`, `guild`
- `subject_id`
- `summary`
- `tags`
- `importance`
- `expires_at`
- `created_at`
- `updated_at`

Each model call should include only 3-6 recent or relevant public memories.

### Relationship Memory

Purpose: make some robots feel familiar with the player and others not.

Suggested table: `ai_robot_relationship_memory`

Fields:

- `id`
- `robot_id`
- `player_id`
- `familiarity`: 0-100
- `attitude`: -100 to 100
- `trust`: 0-100
- `banter_level`: 0-100
- `summary`
- `tags`
- `last_interaction_at`
- `created_at`
- `updated_at`

Behavior:

- Familiarity increases candidate selection chance.
- Positive attitude makes replies warmer or more helpful.
- Negative attitude may make replies cooler or more skeptical, but not abusive.
- Trust makes advice more specific.
- Banter level allows more teasing.

No first-version systems for affection ranks, gifts, romance, or deep companion routes.

## Data Model

This project keeps schema consolidated in `backend-ddd/mythic-realm-starter/src/main/resources/db/latest_schema.sql`. When implementing, add the final schema there rather than creating a migration chain.

### `chat_message` Extensions

Add metadata while preserving normal player-facing rendering:

- `deliver_at`: timestamp when message becomes visible to chat history/SSE.
- `ai_generated`: boolean.
- `ai_interaction_id`: nullable FK-like reference.
- `reply_to_message_id`: nullable source message.
- `generation_reason`: short reason, such as `player_message`, `mention`, `enhancement_event`.

Query behavior:

- Chat history only returns `deliver_at <= CURRENT_TIMESTAMP`.
- SSE only streams messages whose `deliver_at` has arrived.
- AI replies can be inserted immediately and appear later naturally.

### `ai_chat_interaction`

Represents a product-level AI interaction.

Suggested fields:

- `id`
- `channel`
- `trigger_type`: `player_message`, `mention`, `enhancement_event`, `loot_event`, `ambient`, etc.
- `trigger_message_id`
- `trigger_player_id`
- `trigger_robot_id`
- `status`: `pending`, `completed`, `failed`, `skipped`, `fallback`
- `mode`: `live`, `dry_run`, `disabled_fallback`
- `candidate_robot_ids`
- `selected_robot_ids`
- `reply_count`
- `fallback_used`
- `fallback_reason`
- `created_at`
- `completed_at`

### `ai_model_call`

Represents an actual provider/model call. Keep it separate from interaction because one interaction can have zero, one, or multiple model calls.

For this personal learning project, store complete debug material rather than only summaries.

Suggested fields:

- `id`
- `interaction_id`
- `provider`
- `model`
- `feature`: `world_chat`, `guild_chat`, `event_comment`, `memory_update`
- `trigger_type`
- `prompt_version`
- `mode`: `live`, `dry_run`
- `request_payload`: full request JSON/text
- `prompt_text`: full prompt
- `raw_response`: full provider response
- `parsed_response`: parsed structured JSON
- `validation_errors`: full validation errors
- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `token_source`: `provider_reported`, `local_estimated`, `missing`
- `estimated_cost`
- `currency`
- `pricing_snapshot`
- `latency_ms`
- `success`
- `error_code`
- `error_message`
- `created_at`

## AI Usage UI

Add an administrator-only entry under the existing robot activity page.

Ordinary players should not see provider/model/token details.

### Entry Location

First version:

- Add `AI 用量` as a secondary admin-visible entry from the robot activity screen.
- If no formal role system exists, use a local admin allowlist or backend-provided `admin: true`.

### Overview

Show:

- Today's input/output/total tokens.
- Total historical tokens.
- Current provider/model.
- Average latency.
- Failure rate.
- Fallback count.
- Calls by feature.
- Calls by trigger type.
- Calls by model.

### Recent Calls

Table columns:

- Time.
- Feature.
- Trigger.
- Provider/model.
- Prompt/completion/total tokens.
- Token source.
- Latency.
- Success/fallback status.
- Error code.

### Call Detail

Use a detail drawer/modal. Default large fields collapsed.

Show:

- Full request payload.
- Full prompt.
- Full raw response.
- Parsed JSON.
- Validation errors.
- Linked interaction.
- Linked chat messages.
- Candidate/selected robots.
- Prompt version.

## Provider Architecture

Use a provider interface and keep direct model calls out of `ChatService`.

Suggested components:

- `AiDialogueProvider`: interface.
- `TemplateDialogueProvider`: deterministic fallback.
- Real provider implementation, such as an OpenAI-compatible HTTP provider.
- `AiChatInteractionService`: orchestrates interactions, candidate selection, prompt building, model calls, validation, persistence, and reply insertion.
- `AiUsageService`: aggregates usage UI data.

Configuration:

- `ai.chat.enabled`
- `ai.chat.dryRun`
- `ai.chat.maxCallsPerMinute`
- `ai.provider`
- `ai.model`
- `ai.baseUrl`
- `ai.apiKey`
- `ai.timeoutMs`

Provider/model can be changed by configuration. First version UI displays current and historical model data but does not hot-switch models.

## Async Flow

Player message flow:

1. Player sends chat message.
2. `ChatService.send()` sanitizes and saves the player message immediately.
3. `ChatService.send()` returns without waiting for model output.
4. Backend creates `ai_chat_interaction` with `pending` status if AI may run.
5. Async worker selects 5-8 candidate robots.
6. Worker builds prompt and writes `ai_model_call`.
7. Provider returns or fails.
8. Backend stores full request/prompt/response/parsed JSON/errors.
9. Backend validates response.
10. Valid replies are inserted into `chat_message` with future `deliver_at`.
11. Memory updates are applied.
12. Chat history/SSE exposes replies only after `deliver_at`.

## Fallback and Failure Behavior

Player-initiated triggers:

- If model fails, insert at least one template fallback reply.
- If `@robotName` was used, fallback should come from the mentioned robot.

Automatic triggers:

- High-emotion event comment may silently skip if model fails.
- Ambient chatter should silently skip if model fails.

Dry-run:

- Build prompt and record call/interactions.
- Do not publish AI-generated replies.
- Optional config can still publish template fallback for player-facing continuity.

Failure reasons to record:

- `disabled`
- `dry_run`
- `rate_limited`
- `model_timeout`
- `model_error`
- `invalid_json`
- `content_rejected`
- `validation_failed`

## Token and Cost Accounting

Token usage should be based on provider-reported usage when available.

Fallback order:

1. `provider_reported`
2. `local_estimated`
3. `missing`

Store `token_source` per call so UI can distinguish real billing-style usage from estimates.

Store pricing snapshot fields even if first version only displays token counts. This avoids losing historical cost context if prices or model choices change later.

## Prompt Versioning

Record `prompt_version` on every model call.

First version should use code-level constants, for example:

- `robot-chat-v1`
- `robot-event-comment-v1`

Do not build a prompt editor in version 1.

## Safety and Filtering

Use lightweight controls:

- Input length limits.
- Whitespace normalization.
- Output length limits.
- Forbidden phrases around AI/model/system/backend/database/token/prompt.
- JSON schema validation.
- Candidate robot ID validation.
- Fact grounding against prompt-provided facts.
- Template fallback on invalid output.

This is a personal learning project, so do not overbuild production moderation. The priority is preventing immersion-breaking and game-breaking output.

## Implementation Phases

### Phase 1: Persistence and Observability

- Add schema fields/tables.
- Add AI usage records.
- Add admin AI usage page.
- Keep existing template chat behavior.

### Phase 2: Async Interaction Pipeline

- Add `AiChatInteractionService`.
- Add candidate selection.
- Add `chat_message.deliver_at` filtering in history/SSE.
- Add template provider/fallback through the new pipeline.

### Phase 3: Real Provider

- Add real provider behind `AiDialogueProvider`.
- Add prompt builder.
- Add JSON validation.
- Add usage accounting from provider usage.
- Add dry-run and rate limiting.

### Phase 4: Memory

- Add relationship memory.
- Add public memory.
- Include relevant memory in prompt.
- Apply validated memory updates.

### Phase 5: High-Emotion Event Comments

- Wire enhancement, loot, dungeon clear, rift, arena, market, and guild events.
- Keep ordinary events silent.
- Track event comments in usage UI.

## Acceptance Criteria

Experience:

- Player sends 10 varied world chat messages; at least 8 receive natural replies.
- `@robotName` prioritizes that robot.
- Replies appear with staggered timing, not all in the same instant.
- Replies sound like game players, not NPCs or support.
- Multiple replies do not all say the same thing.
- Robots may disagree lightly without inventing facts.
- Robots do not self-identify as AI/model/system/backend.

Cost:

- Most normal interactions stay within 1k-3k tokens.
- Usage UI shows prompt, completion, total tokens, token source, model, latency, and errors.

Reliability:

- AI disabled mode keeps chat usable through templates.
- Model failure on player chat produces fallback.
- Model failure on automatic ambient/event comments can skip safely.
- Invalid JSON never reaches player-facing chat.

Admin UI:

- AI usage entry is visible only to admin/developer users.
- Recent model calls can be inspected with full prompt and full response.
- Calls link back to interactions and generated chat messages.

Gameplay boundaries:

- LLM output cannot directly buy, sell, enhance, run dungeons, recharge, grant rewards, or mutate game state.
- Real robot gameplay decisions remain in the existing rule-driven robot decision engine.
