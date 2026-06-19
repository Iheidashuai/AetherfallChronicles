package com.mythicrealm.api.gameplay.home;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.combat.CombatPowerService;
import com.mythicrealm.api.gameplay.combat.CombatStats;
import com.mythicrealm.api.gameplay.combat.CombatStatsService;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.skill.SkillService;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class HomeController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final GameConfigService gameConfigService;
    private final CombatStatsService combatStatsService;
    private final CombatPowerService combatPowerService;
    private final SkillService skillService;
    private final StaminaService staminaService;

    public HomeController(
        SessionService sessionService,
        PlayerService playerService,
        InventoryService inventoryService,
        GameConfigService gameConfigService,
        CombatStatsService combatStatsService,
        CombatPowerService combatPowerService,
        SkillService skillService,
        StaminaService staminaService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.gameConfigService = gameConfigService;
        this.combatStatsService = combatStatsService;
        this.combatPowerService = combatPowerService;
        this.skillService = skillService;
        this.staminaService = staminaService;
    }

    @GetMapping("/home")
    HomeSnapshot home(@RequestHeader(name = "Authorization", required = false) String authorization) {
        var account = sessionService.require(authorization);
        PlayerRecord player = playerService.requireByAccount(account);
        Map<String, ItemRecord> equipped = inventoryService.equippedItems(player.id());
        List<ItemRecord> inventory = inventoryService.inventoryItems(player.id());
        CombatStats stats = combatStatsService.playerStats(player, equipped.values());
        CombatStats baseStats = combatStatsService.playerStats(player, List.of());
        int equipmentPower = equipped.values().stream().mapToInt(inventoryService::equipmentPower).sum();
        int basePower = combatPowerService.basePower(baseStats);
        int skillPower = skillService.skillPower(player);
        int totalPower = combatPowerService.combatPower(stats, baseStats, equipmentPower) + skillPower;
        return new HomeSnapshot(
            player,
            totalPower,
            stats.maxHp(),
            stats.maxMp(),
            DerivedStats.from(stats),
            DerivedStats.from(baseStats),
            DerivedStats.difference(stats, baseStats),
            equipmentPower,
            new PowerBreakdown(basePower, equipmentPower, skillPower, Math.max(0, totalPower - basePower - equipmentPower - skillPower), totalPower),
            equipped,
            inventory.size(),
            InventoryService.MAX_SLOTS,
            gameConfigService.summary(),
            staminaService.snapshot(player.id())
        );
    }

    public record HomeSnapshot(
        PlayerRecord player,
        int combatPower,
        double maxHp,
        double maxMp,
        DerivedStats derivedStats,
        DerivedStats baseStats,
        DerivedStats equipmentStats,
        int equipmentPower,
        PowerBreakdown powerBreakdown,
        Map<String, ItemRecord> equippedItems,
        int inventoryCount,
        int inventoryCapacity,
        GameConfigService.ConfigSummary config,
        StaminaSnapshot stamina
    ) {
    }

    public record DerivedStats(
        int maxHp,
        int maxMp,
        int attackPower,
        int armor,
        int resistance,
        int speed,
        double accuracy,
        double evasion,
        double critChance,
        double critDamage
    ) {
        static DerivedStats from(CombatStats stats) {
            return new DerivedStats(
                stats.maxHp(),
                stats.maxMp(),
                stats.attackPower(),
                stats.armor(),
                stats.resistance(),
                stats.speed(),
                stats.accuracy(),
                stats.evasion(),
                stats.critChance(),
                stats.critDamage()
            );
        }

        static DerivedStats difference(CombatStats total, CombatStats base) {
            return new DerivedStats(
                total.maxHp() - base.maxHp(),
                total.maxMp() - base.maxMp(),
                total.attackPower() - base.attackPower(),
                total.armor() - base.armor(),
                total.resistance() - base.resistance(),
                total.speed() - base.speed(),
                Math.max(0, total.accuracy() - base.accuracy()),
                Math.max(0, total.evasion() - base.evasion()),
                Math.max(0, total.critChance() - base.critChance()),
                Math.max(0, total.critDamage() - base.critDamage())
            );
        }
    }

    public record PowerBreakdown(
        int basePower,
        int equipmentPower,
        int skillPower,
        int synergyPower,
        int totalPower
    ) {
    }
}
