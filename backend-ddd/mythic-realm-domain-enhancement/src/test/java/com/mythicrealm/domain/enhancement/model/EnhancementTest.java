package com.mythicrealm.domain.enhancement.model;

import com.mythicrealm.domain.enhancement.valueobject.EnhancementLevel;
import com.mythicrealm.domain.enhancement.valueobject.EnhancementLuck;
import com.mythicrealm.domain.enhancement.valueobject.EnhancementResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnhancementTest {

    @Test
    void testCreateEnhancement() {
        Enhancement enhancement = Enhancement.create(1L, 100L);

        assertEquals(1L, enhancement.getItemId());
        assertEquals(100L, enhancement.getPlayerId());
        assertEquals(0, enhancement.getLevelValue());
        assertEquals(0, enhancement.getLuckValue());
    }

    @Test
    void testRestoreEnhancement() {
        Enhancement enhancement = Enhancement.restore(1L, 100L, 5, 3);

        assertEquals(5, enhancement.getLevelValue());
        assertEquals(3, enhancement.getLuckValue());
    }

    @Test
    void testSuccessfulEnhancement() {
        Enhancement enhancement = Enhancement.create(1L, 100L);

        EnhancementResult result = enhancement.attemptEnhancement(true, 100, 0.8);

        assertTrue(result.success());
        assertEquals(0, result.previousLevel().value());
        assertEquals(1, result.currentLevel().value());
        assertEquals(0, result.currentLuck().value()); // 成功后幸运值清零
        assertEquals(100, result.cost());
        assertEquals(0.8, result.successRate());
    }

    @Test
    void testFailedEnhancementInSafeZone() {
        Enhancement enhancement = Enhancement.restore(1L, 100L, 3, 0);

        EnhancementResult result = enhancement.attemptEnhancement(false, 400, 1.0);

        assertFalse(result.success());
        assertEquals(3, result.previousLevel().value());
        assertEquals(3, result.currentLevel().value()); // 1-6级失败不掉级
        assertEquals(1, result.currentLuck().value()); // 失败增加幸运值
    }

    @Test
    void testFailedEnhancementInNormalZone() {
        Enhancement enhancement = Enhancement.restore(1L, 100L, 9, 0);

        EnhancementResult result = enhancement.attemptEnhancement(false, 8100, 0.6);

        assertFalse(result.success());
        assertEquals(9, result.previousLevel().value());
        assertEquals(8, result.currentLevel().value()); // 7-12级失败掉1级
        assertEquals(1, result.currentLuck().value());
    }

    @Test
    void testFailedEnhancementInRiskyZone() {
        Enhancement enhancement = Enhancement.restore(1L, 100L, 14, 0);

        EnhancementResult result = enhancement.attemptEnhancement(false, 21000, 0.2);

        assertFalse(result.success());
        assertEquals(14, result.previousLevel().value());
        assertEquals(12, result.currentLevel().value()); // 13-15级失败掉2级
        assertEquals(1, result.currentLuck().value());
    }

    @Test
    void testLuckAccumulation() {
        Enhancement enhancement = Enhancement.restore(1L, 100L, 5, 2);

        // 第一次失败
        enhancement.attemptEnhancement(false, 600, 0.8);
        assertEquals(3, enhancement.getLuckValue());

        // 第二次失败
        enhancement.attemptEnhancement(false, 700, 0.8);
        assertEquals(4, enhancement.getLuckValue());

        // 成功后幸运值清零
        enhancement.attemptEnhancement(true, 700, 0.8);
        assertEquals(0, enhancement.getLuckValue());
    }

    @Test
    void testCannotEnhanceBeyondMaxLevel() {
        Enhancement enhancement = Enhancement.restore(1L, 100L, 15, 0);

        assertThrows(IllegalStateException.class, () -> {
            enhancement.attemptEnhancement(true, 22500, 0.2);
        });
    }

    @Test
    void testEnhancementResultLevelChanged() {
        Enhancement enhancement = Enhancement.restore(1L, 100L, 5, 0);

        // 成功：等级改变
        EnhancementResult successResult = enhancement.attemptEnhancement(true, 600, 0.8);
        assertTrue(successResult.isLevelChanged());
        assertEquals(1, successResult.getLevelDifference());
        assertFalse(successResult.isLevelDecreased());

        // 失败且掉级：等级改变
        Enhancement enhancement2 = Enhancement.restore(2L, 100L, 9, 0);
        EnhancementResult failResult = enhancement2.attemptEnhancement(false, 8100, 0.6);
        assertTrue(failResult.isLevelChanged());
        assertEquals(-1, failResult.getLevelDifference());
        assertTrue(failResult.isLevelDecreased());
    }
}
