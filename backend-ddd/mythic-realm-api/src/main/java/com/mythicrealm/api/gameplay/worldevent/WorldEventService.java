package com.mythicrealm.api.gameplay.worldevent;

import com.mythicrealm.api.gameplay.guild.GuildBossService;
import com.mythicrealm.api.gameplay.guild.GuildService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService;
import com.mythicrealm.api.gameplay.market.MarketService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.robot.RobotActivityLogService;
import com.mythicrealm.api.gameplay.robot.RobotActivityLogService.RobotActivityEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class WorldEventService {
    private static final int MAX_EVENTS = 5;
    private static final List<String> ROBOT_HIGHLIGHT_KINDS = List.of(
        "enhance",
        "processing_ascend",
        "processing_reforge",
        "guild_boss",
        "guild_donate",
        "market_buy",
        "market_list",
        "recharge",
        "arena",
        "rift",
        "dungeon"
    );

    private final GuildService guildService;
    private final GuildBossService guildBossService;
    private final MarketService marketService;
    private final InventoryService inventoryService;
    private final LeaderboardService leaderboardService;
    private final RobotActivityLogService robotActivityLogService;

    public WorldEventService(
        GuildService guildService,
        GuildBossService guildBossService,
        MarketService marketService,
        InventoryService inventoryService,
        LeaderboardService leaderboardService,
        RobotActivityLogService robotActivityLogService
    ) {
        this.guildService = guildService;
        this.guildBossService = guildBossService;
        this.marketService = marketService;
        this.inventoryService = inventoryService;
        this.leaderboardService = leaderboardService;
        this.robotActivityLogService = robotActivityLogService;
    }

    public List<WorldEvent> events(PlayerRecord player) {
        List<WorldEvent> candidates = new ArrayList<>();
        guildBossEvent(player).ifPresent(candidates::add);
        marketUpgradeEvent(player).ifPresent(candidates::add);
        leaderboardNeighborEvent(player).ifPresent(candidates::add);
        candidates.addAll(robotHighlightEvents());
        return candidates.stream()
            .sorted(Comparator.comparingInt(WorldEvent::priority).reversed())
            .limit(MAX_EVENTS)
            .toList();
    }

    private Optional<WorldEvent> guildBossEvent(PlayerRecord player) {
        GuildService.GuildView guild = guildService.myGuild(player);
        if (guild == null) {
            return Optional.of(new WorldEvent(
                "guild-join",
                "guild_boss",
                45,
                "还没有加入公会",
                "中期生态从公会开始。选择一个冒险者公会后，就能参与每周 Boss 和贡献榜。",
                "加入公会后，冒险者队友的 Boss、捐献和聊天会变成与你相关的世界事件。",
                "解锁公会 Boss、公会币和周榜",
                "今日",
                "social",
                WorldEventAction.to("选择公会", "guild")
            ));
        }
        GuildBossService.GuildBossView view = guildBossService.bossFor(player);
        GuildBossService.BossInfo boss = view.boss();
        if (!"alive".equals(boss.status())) {
            return Optional.empty();
        }
        double hpPct = boss.hpMax() <= 0 ? 100.0 : boss.hpCurrent() * 100.0 / boss.hpMax();
        int priority = hpPct <= 10 ? 96 : hpPct <= 25 ? 91 : hpPct <= 50 ? 84 : 72;
        String title = hpPct <= 25
            ? "公会 Boss 濒危"
            : "公会 Boss 正在推进";
        String relevance = view.myRank() > 0
            ? "你当前贡献第 " + view.myRank() + "，再打一轮可能提升本周贡献排名。"
            : "你本周还没有出手，第一刀会立刻进入公会贡献榜。";
        return Optional.of(new WorldEvent(
            "guild-boss-" + boss.id(),
            "guild_boss",
            priority,
            title,
            boss.name() + " 剩余 " + Math.max(0, Math.round(hpPct)) + "% 血量。",
            relevance,
            "公会币、贡献排名、周奖励",
            "本周",
            hpPct <= 25 ? "urgent" : "social",
            WorldEventAction.to("支援公会", "guild", String.valueOf(boss.id()), Map.of("focus", "boss"))
        ));
    }

    private Optional<WorldEvent> marketUpgradeEvent(PlayerRecord player) {
        Map<String, ItemRecord> equipped = inventoryService.equippedItems(player.id());
        MarketService.MarketSnapshot market = marketService.listings(player);
        return market.listings().stream()
            .filter(listing -> !listing.playerListing())
            .filter(listing -> "equipment".equals(listing.item().itemCategory()))
            .filter(listing -> listing.item().requiredLevel() <= player.level())
            .filter(listing -> listing.price() <= player.gold())
            .map(listing -> upgradeCandidate(player, equipped, listing))
            .flatMap(Optional::stream)
            .max(Comparator
                .comparingInt(MarketUpgradeCandidate::score)
                .thenComparingInt(candidate -> -candidate.listing().price()))
            .map(candidate -> {
                MarketService.MarketListingView listing = candidate.listing();
                return new WorldEvent(
                    "market-upgrade-" + listing.id(),
                    "market_upgrade",
                    Math.min(94, 68 + Math.max(0, candidate.powerGain() / 200)),
                    "商会出现可提升装备",
                    "【" + listing.item().name() + "】比你当前部位预计 +" + candidate.powerGain() + " 战力。",
                    "你现在买得起，等级也可用；卖家是 " + listing.sellerName() + "。",
                    listing.price() + " 金 · " + qualityName(listing.item().quality()),
                    freshness(listing.listedAt()),
                    "opportunity",
                    WorldEventAction.to("抢下装备", "market", String.valueOf(listing.id()), Map.of("listingId", listing.id()))
                );
            });
    }

    private Optional<MarketUpgradeCandidate> upgradeCandidate(
        PlayerRecord player,
        Map<String, ItemRecord> equipped,
        MarketService.MarketListingView listing
    ) {
        ItemRecord item = itemFromListing(player.id(), listing.item());
        int candidatePower = inventoryService.equipmentPower(item);
        int currentPower = currentSlotPower(equipped, listing.item().itemType());
        int gain = candidatePower - currentPower;
        if (gain <= 0) {
            return Optional.empty();
        }
        int priceRatioScore = Math.max(0, 300 - listing.priceRatio());
        int qualityScore = inventoryService.qualityRank(listing.item().quality()) * 80;
        int score = gain + priceRatioScore + qualityScore;
        return Optional.of(new MarketUpgradeCandidate(listing, gain, score));
    }

    private int currentSlotPower(Map<String, ItemRecord> equipped, String itemType) {
        if ("ring".equals(itemType)) {
            int ring1 = equipped.containsKey("ring1") ? inventoryService.equipmentPower(equipped.get("ring1")) : 0;
            int ring2 = equipped.containsKey("ring2") ? inventoryService.equipmentPower(equipped.get("ring2")) : 0;
            if (ring1 == 0 || ring2 == 0) {
                return 0;
            }
            return Math.min(ring1, ring2);
        }
        ItemRecord current = equipped.get(itemType);
        return current == null ? 0 : inventoryService.equipmentPower(current);
    }

    private ItemRecord itemFromListing(long playerId, MarketService.ItemSnapshot snapshot) {
        return new ItemRecord(
            0,
            playerId,
            snapshot.templateId(),
            snapshot.name(),
            snapshot.itemType(),
            snapshot.itemCategory(),
            snapshot.quality(),
            snapshot.requiredLevel(),
            snapshot.attackBonus(),
            snapshot.defenseBonus(),
            snapshot.resistanceBonus(),
            snapshot.hpBonus(),
            snapshot.mpBonus(),
            BigDecimal.valueOf(snapshot.critBonus()),
            snapshot.sellPrice(),
            snapshot.quantity(),
            snapshot.stackable(),
            snapshot.effectType(),
            snapshot.effectValueJson(),
            0,
            1,
            15,
            snapshot.enhancementLevel(),
            snapshot.enhancementLuck(),
            snapshot.refineLevel(),
            "balanced",
            snapshot.ascensionLevel(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            List.of(),
            List.of(),
            snapshot.description()
        );
    }

    private Optional<WorldEvent> leaderboardNeighborEvent(PlayerRecord player) {
        List<LeaderboardService.LeaderboardEntry> entries = leaderboardService.entries(player);
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).player()) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return Optional.empty();
        }
        LeaderboardService.LeaderboardEntry me = entries.get(index);
        if (index > 0) {
            LeaderboardService.LeaderboardEntry above = entries.get(index - 1);
            int gap = above.power() - me.power();
            if (gap <= Math.max(800, me.power() / 8)) {
                return Optional.of(new WorldEvent(
                    "leaderboard-above-" + above.rank(),
                    "leaderboard_neighbor",
                    76,
                    "再提升一点就能反超",
                    "你距离 #" + above.rank() + " " + above.name() + " 还差 " + gap + " 战力。",
                    "刷一轮装备、强化或买下市场机会，都可能让你往前挤一名。",
                    "战力榜排名压力",
                    "现在",
                    "competitive",
                    WorldEventAction.to("查看对手", "leaderboard", above.name(), Map.of("focus", "nearby"))
                ));
            }
        }
        if (index + 1 < entries.size()) {
            LeaderboardService.LeaderboardEntry below = entries.get(index + 1);
            int gap = me.power() - below.power();
            if (gap <= Math.max(600, me.power() / 10)) {
                return Optional.of(new WorldEvent(
                    "leaderboard-below-" + below.rank(),
                    "leaderboard_neighbor",
                    70,
                    "有人快追上你了",
                    "#" + below.rank() + " " + below.name() + " 距离你只差 " + gap + " 战力。",
                    "如果今天不继续变强，你的排名可能很快被其他冒险者挤下去。",
                    "守住战力榜位置",
                    "现在",
                    "competitive",
                    WorldEventAction.to("查看排名", "leaderboard", below.name(), Map.of("focus", "nearby"))
                ));
            }
        }
        return Optional.empty();
    }

    private List<WorldEvent> robotHighlightEvents() {
        return robotActivityLogService.latestEvents(30, ROBOT_HIGHLIGHT_KINDS).stream()
            .limit(2)
            .map(event -> new WorldEvent(
                "robot-" + event.robotId() + "-" + event.createdAt().toEpochMilli() + "-" + Math.abs(event.text().hashCode()),
                "robot_highlight",
                robotEventPriority(event),
                robotEventTitle(event),
                event.text(),
                event.actorName() + " 是正在活动的冒险者，这类动态会影响市场、公会或榜单氛围。",
                robotRewardHint(event.kind()),
                freshness(event.createdAt()),
                robotEventTone(event.kind()),
                WorldEventAction.to("查看冒险者", "robots", String.valueOf(event.robotId()), Map.of("robotId", event.robotId()))
            ))
            .toList();
    }

    private int robotEventPriority(RobotActivityEvent event) {
        return switch (event.kind()) {
            case "recharge", "processing_ascend" -> 64;
            case "guild_boss", "guild_donate" -> 62;
            case "market_buy", "market_list" -> 60;
            case "enhance", "rift", "arena" -> 58;
            default -> 54;
        };
    }

    private String robotEventTitle(RobotActivityEvent event) {
        return switch (event.kind()) {
            case "recharge" -> "富豪冒险者又出手了";
            case "guild_boss" -> "公会队友正在打 Boss";
            case "guild_donate" -> "公会队友完成捐献";
            case "market_buy", "market_list" -> "商会里有冒险者行动";
            case "enhance", "processing_ascend", "processing_reforge" -> "冒险者正在提升装备";
            case "arena" -> "竞技场排名正在变化";
            case "rift" -> "有人在推进深渊裂隙";
            default -> "冒险者世界有新动态";
        };
    }

    private String robotRewardHint(String kind) {
        return switch (kind) {
            case "guild_boss", "guild_donate" -> "公会贡献与世界氛围";
            case "market_buy", "market_list" -> "市场供需变化";
            case "recharge" -> "财富榜与金币流动";
            case "arena" -> "竞技场竞争";
            case "rift" -> "终局推进";
            default -> "冒险者高光";
        };
    }

    private String robotEventTone(String kind) {
        return switch (kind) {
            case "market_buy", "market_list" -> "opportunity";
            case "arena" -> "competitive";
            case "guild_boss", "guild_donate" -> "social";
            case "recharge" -> "ambient";
            default -> "ambient";
        };
    }

    private String freshness(Instant createdAt) {
        if (createdAt == null) {
            return "最近";
        }
        long minutes = Duration.between(createdAt, Instant.now()).toMinutes();
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + " 分钟前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " 小时前";
        }
        return "今日";
    }

    private String qualityName(String quality) {
        return switch (quality) {
            case "immortal" -> "不朽";
            case "legendary" -> "传说";
            case "epic" -> "史诗";
            case "rare" -> "稀有";
            case "uncommon" -> "优秀";
            default -> "普通";
        };
    }

    private record MarketUpgradeCandidate(MarketService.MarketListingView listing, int powerGain, int score) {
    }
}
