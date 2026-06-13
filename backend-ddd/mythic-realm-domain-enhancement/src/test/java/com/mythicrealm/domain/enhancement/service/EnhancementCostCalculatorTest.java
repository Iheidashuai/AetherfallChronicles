package com.mythicrealm.domain.enhancement.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnhancementCostCalculatorTest {

    private final EnhancementCostCalculator calculator = new EnhancementCostCalculator();

    @Test
    void testCalculateCost() {
        // 公式: 装备等级² × 目标强化等级 × 10

        // 10级装备强化到+1
        assertEquals(1000, calculator.calculate(10, 1)); // 10² × 1 × 10 = 1000

        // 10级装备强化到+5
        assertEquals(5000, calculator.calculate(10, 5)); // 10² × 5 × 10 = 5000

        // 20级装备强化到+10
        assertEquals(40000, calculator.calculate(20, 10)); // 20² × 10 × 10 = 40000

        // 30级装备强化到+15
        assertEquals(135000, calculator.calculate(30, 15)); // 30² × 15 × 10 = 135000
    }

    @Test
    void testCalculateCostWithMinLevel() {
        // 装备等级最小为1
        assertEquals(10, calculator.calculate(0, 1)); // 1² × 1 × 10 = 10
        assertEquals(50, calculator.calculate(0, 5)); // 1² × 5 × 10 = 50
    }

    @Test
    void testCostIncreasesByLevel() {
        // 同一装备，强化等级越高，成本越高
        assertTrue(calculator.calculate(10, 1) < calculator.calculate(10, 5));
        assertTrue(calculator.calculate(10, 5) < calculator.calculate(10, 10));
        assertTrue(calculator.calculate(10, 10) < calculator.calculate(10, 15));
    }

    @Test
    void testCostIncreasesByItemLevel() {
        // 同一强化等级，装备等级越高，成本越高（平方增长）
        assertTrue(calculator.calculate(10, 5) < calculator.calculate(20, 5));
        assertTrue(calculator.calculate(20, 5) < calculator.calculate(30, 5));

        // 验证平方增长
        int cost10 = calculator.calculate(10, 5);
        int cost20 = calculator.calculate(20, 5);
        assertEquals(cost10 * 4, cost20); // (20/10)² = 4
    }
}
