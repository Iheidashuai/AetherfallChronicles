package com.mythicrealm.api.gameplay.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class ItemEffectEngineTest {

    @Test
    void appliesConfiguredCompositeEffects() {
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerService playerService = mock(PlayerService.class);
        StaminaService staminaService = mock(StaminaService.class);
        PlayerRecord player = new PlayerRecord(10, 1, "测试者", "warrior", 20, 0, 1000, 10, 5, 8, 3, 4, 0);
        StaminaSnapshot before = new StaminaSnapshot(100, 1000, 0, 0, Instant.now());
        StaminaSnapshot after = new StaminaSnapshot(150, 1000, 0, 0, Instant.now());
        ItemRecord item = consumable(
            """
            {"version":1,"tags":["stamina"],"ui":{"actionLabel":"使用","summary":"测试组合"},"use":{"consumeSelf":1,"effects":[{"op":"restoreStamina","amount":50},{"op":"grantItems","items":[{"templateId":"mat_test","quantity":2}]},{"op":"emitQuestEvent","type":"customEvent","targetId":"mat_test","amount":3}]}}
            """
        );
        ItemRecord reward = consumable("{}");
        @SuppressWarnings("unchecked")
        ObjectProvider<InventoryService> inventoryProvider = mock(ObjectProvider.class);

        when(playerService.requireById(player.id())).thenReturn(player);
        when(inventoryProvider.getObject()).thenReturn(inventoryService);
        when(staminaService.snapshot(player.id())).thenReturn(before);
        when(staminaService.add(player.id(), 50)).thenReturn(after);
        when(inventoryService.grantItem(org.mockito.ArgumentMatchers.eq(player.id()), org.mockito.ArgumentMatchers.eq("mat_test"), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.any(Random.class)))
            .thenReturn(List.of(reward));

        ItemEffectEngine engine = new ItemEffectEngine(
            mock(JdbcTemplate.class),
            playerService,
            staminaService,
            mock(AnnouncementService.class),
            new ObjectMapper(),
            inventoryProvider
        );

        ItemEffectEngine.ApplyResult result = engine.apply(player, item);

        verify(inventoryService).consumeOne(player.id(), item.id());
        assertThat(result.message()).contains("测试组合");
        assertThat(result.stamina()).isEqualTo(after);
        assertThat(result.rewards()).containsExactly(reward);
        assertThat(result.events()).contains(new ItemEffectEvent("customEvent", "mat_test", 3));
    }

    private static ItemRecord consumable(String effectValueJson) {
        return new ItemRecord(
            1,
            10,
            "item_test",
            "测试物品",
            "potion",
            "consumable",
            "rare",
            1,
            0,
            0,
            0,
            0,
            0,
            BigDecimal.ZERO,
            1,
            1,
            true,
            "configured",
            effectValueJson,
            0,
            1,
            15,
            0,
            0,
            0,
            "balanced",
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
            0,
            List.of(),
            List.of(),
            ""
        );
    }
}
