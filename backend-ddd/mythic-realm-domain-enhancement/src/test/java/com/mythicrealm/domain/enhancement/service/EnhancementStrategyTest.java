package com.mythicrealm.domain.enhancement.service;

import com.mythicrealm.domain.enhancement.model.Enhancement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnhancementStrategyTest {

    @Test
    void testSafeStrategy() {
        EnhancementStrategy strategy = new SafeEnhancementStrategy();

        assertTrue(strategy.supports(1));
        assertTrue(strategy.supports(6));
        assertFalse(strategy.supports(7));

        assertEquals(1.0, strategy.calculateBaseSuccessRate(1));
        assertEquals(1.0, strategy.calculateBaseSuccessRate(3));
        assertEquals(0.8, strategy.calculateBaseSuccessRate(4));
        assertEquals(0.8, strategy.calculateBaseSuccessRate(6));
    }

    @Test
    void testNormalStrategy() {
        EnhancementStrategy strategy = new NormalEnhancementStrategy();

        assertTrue(strategy.supports(7));
        assertTrue(strategy.supports(12));
        assertFalse(strategy.supports(6));
        assertFalse(strategy.supports(13));

        assertEquals(0.6, strategy.calculateBaseSuccessRate(7));
        assertEquals(0.6, strategy.calculateBaseSuccessRate(9));
        assertEquals(0.4, strategy.calculateBaseSuccessRate(10));
        assertEquals(0.4, strategy.calculateBaseSuccessRate(12));
    }

    @Test
    void testRiskyStrategy() {
        EnhancementStrategy strategy = new RiskyEnhancementStrategy();

        assertTrue(strategy.supports(13));
        assertTrue(strategy.supports(15));
        assertFalse(strategy.supports(12));

        assertEquals(0.2, strategy.calculateBaseSuccessRate(13));
        assertEquals(0.2, strategy.calculateBaseSuccessRate(15));
    }

    @Test
    void testFinalSuccessRateWithLuck() {
        EnhancementStrategy strategy = new SafeEnhancementStrategy();

        // 无幸运值：按目标强化等级计算基础成功率（+3 冲 +4 为 80%）
        Enhancement enhancement1 = Enhancement.restore(1L, 100L, 3, 0);
        assertEquals(0.8, strategy.calculateFinalSuccessRate(enhancement1));

        // 有幸运值：基础成功率 + 幸运值加成
        Enhancement enhancement2 = Enhancement.restore(2L, 100L, 3, 3);
        assertEquals(0.95, strategy.calculateFinalSuccessRate(enhancement2), 0.000001); // 0.8 + 0.15

        // 幸运值加成示例
        Enhancement enhancement3 = Enhancement.restore(3L, 100L, 5, 2);
        assertEquals(0.9, strategy.calculateFinalSuccessRate(enhancement3)); // 0.8 + 0.1
    }

    @Test
    void testFinalSuccessRateNotExceedOne() {
        EnhancementStrategy strategy = new SafeEnhancementStrategy();

        // 即使幸运值很高，成功率也不会超过100%
        Enhancement enhancement = Enhancement.restore(1L, 100L, 3, 10);
        assertEquals(1.0, strategy.calculateFinalSuccessRate(enhancement));
    }
}
