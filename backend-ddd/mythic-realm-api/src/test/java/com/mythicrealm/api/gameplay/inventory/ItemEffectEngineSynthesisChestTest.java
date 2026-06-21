package com.mythicrealm.api.gameplay.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ItemEffectEngineSynthesisChestTest {
    @Test
    void immortalEquipmentChestRollsAcrossNineWearableSlots() throws Exception {
        ItemEffectEngine engine = new ItemEffectEngine(null, null, null, null, new ObjectMapper(), null);
        Method method = ItemEffectEngine.class.getDeclaredMethod(
            "synthesisChestGear",
            String.class,
            int.class,
            Random.class
        );
        method.setAccessible(true);
        LastIndexRandom random = new LastIndexRandom();

        Object gear = method.invoke(engine, "chest_immortal_cache", 90, random);

        assertThat(random.lastBound).isEqualTo(9);
        assertThat(gear.getClass().getDeclaredMethod("templateId").invoke(gear)).isEqualTo("eq_bloodmoon_l90_ring_immortal");
        assertThat(gear.getClass().getDeclaredMethod("slot").invoke(gear)).isEqualTo("ring2");
    }

    private static final class LastIndexRandom extends Random {
        private int lastBound;

        @Override
        public int nextInt(int bound) {
            lastBound = bound;
            return bound - 1;
        }
    }
}
