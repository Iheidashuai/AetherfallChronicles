package com.mythicrealm.api.gameplay.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryServiceStackingTest {

    @Test
    void stackableGrantAddsToExistingStackEvenWhenLarge() {
        List<InventoryService.StackableGrantStep> plan = InventoryService.planStackableGrant(
            List.of(new InventoryService.StackableStack(10L, 999)),
            2
        );

        assertThat(plan).containsExactly(new InventoryService.StackableGrantStep(10L, 2));
    }

    @Test
    void stackableGrantUsesOnlyOneExistingInventorySlot() {
        List<InventoryService.StackableGrantStep> plan = InventoryService.planStackableGrant(
            List.of(new InventoryService.StackableStack(10L, 998)),
            3
        );

        assertThat(plan).containsExactly(new InventoryService.StackableGrantStep(10L, 3));
    }

    @Test
    void stackableGrantCreatesOneStackForLargeNewAmount() {
        List<InventoryService.StackableGrantStep> plan = InventoryService.planStackableGrant(
            List.of(),
            2_005
        );

        assertThat(plan).containsExactly(new InventoryService.StackableGrantStep(null, 2_005));
    }

    @Test
    void equipmentTransferRequiresSameEquipmentSlot() {
        assertThat(InventoryService.canTransferEquipmentProgressBetween(
            equipment(1, "legs", 15),
            equipment(2, "legs", 0)
        )).isTrue();

        assertThat(InventoryService.canTransferEquipmentProgressBetween(
            equipment(1, "legs", 15),
            equipment(2, "weapon", 0)
        )).isFalse();
    }

    @Test
    void equipmentTransferSourceRequiresAnyProgress() {
        assertThat(InventoryService.hasTransferableEquipmentProgress(equipment(1, "legs", 0))).isFalse();
        assertThat(InventoryService.hasTransferableEquipmentProgress(equipment(2, "legs", 1))).isTrue();
    }

    @Test
    void synthesizedChestGearNamesDistinguishRingSides() {
        assertThat(InventoryService.synthesizedChestGearName("永夜·永恒指轮", "ring1")).isEqualTo("永夜·永恒指轮·左戒");
        assertThat(InventoryService.synthesizedChestGearName("永夜·永恒指轮", "ring2")).isEqualTo("永夜·永恒指轮·右戒");
        assertThat(InventoryService.synthesizedChestGearName("永夜·弑神之刃", "weapon")).isEqualTo("永夜·弑神之刃");
    }

    private static ItemRecord equipment(long id, String itemType, int enhancementLevel) {
        return new ItemRecord(
            id,
            1L,
            "test_" + itemType,
            "测试装备",
            itemType,
            "rare",
            1,
            1,
            1,
            1,
            1,
            1,
            BigDecimal.ZERO,
            1,
            enhancementLevel,
            0
        );
    }
}
