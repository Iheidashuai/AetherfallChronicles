package com.mythicrealm.api.gameplay.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryServiceStackingTest {

    @Test
    void stackableGrantCreatesNewStackWhenExistingStackIsFull() {
        List<InventoryService.StackableGrantStep> plan = InventoryService.planStackableGrant(
            List.of(new InventoryService.StackableStack(10L, 999)),
            999,
            2
        );

        assertThat(plan).containsExactly(new InventoryService.StackableGrantStep(null, 2));
    }

    @Test
    void stackableGrantFillsPartialStackBeforeCreatingOverflowStack() {
        List<InventoryService.StackableGrantStep> plan = InventoryService.planStackableGrant(
            List.of(new InventoryService.StackableStack(10L, 998)),
            999,
            3
        );

        assertThat(plan).containsExactly(
            new InventoryService.StackableGrantStep(10L, 1),
            new InventoryService.StackableGrantStep(null, 2)
        );
    }

    @Test
    void stackableGrantSplitsLargeOverflowIntoMaxSizedStacks() {
        List<InventoryService.StackableGrantStep> plan = InventoryService.planStackableGrant(
            List.of(),
            999,
            2_005
        );

        assertThat(plan).containsExactly(
            new InventoryService.StackableGrantStep(null, 999),
            new InventoryService.StackableGrantStep(null, 999),
            new InventoryService.StackableGrantStep(null, 7)
        );
    }
}
