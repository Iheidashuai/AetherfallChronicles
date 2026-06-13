package com.mythicrealm.api.gameplay.recharge;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class WealthTierService {
    public static final int TOP_TIER_LEVEL = 15;

    private static final List<WealthTier> TIERS = List.of(
        new WealthTier(0, "L00", "贫民", 320, 3_000, 4_500, 1.05, 92),
        new WealthTier(1, "L01", "节俭平民", 260, 4_500, 6_500, 1.08, 96),
        new WealthTier(2, "L02", "稳定打工人", 210, 6_500, 9_500, 1.12, 100),
        new WealthTier(3, "L03", "小康玩家", 170, 9_500, 15_000, 1.18, 105),
        new WealthTier(4, "L04", "活跃玩家", 130, 15_000, 25_000, 1.25, 110),
        new WealthTier(5, "L05", "轻氪玩家", 95, 25_000, 45_000, 1.35, 116),
        new WealthTier(6, "L06", "月卡党", 70, 45_000, 80_000, 1.50, 122),
        new WealthTier(7, "L07", "进阶氪金者", 52, 80_000, 140_000, 1.70, 130),
        new WealthTier(8, "L08", "小资高玩", 38, 140_000, 240_000, 1.95, 138),
        new WealthTier(9, "L09", "装备收藏家", 27, 240_000, 400_000, 2.20, 146),
        new WealthTier(10, "L10", "商会大户", 19, 400_000, 650_000, 2.55, 154),
        new WealthTier(11, "L11", "冲榜玩家", 13, 650_000, 1_000_000, 2.95, 164),
        new WealthTier(12, "L12", "土豪", 8, 1_000_000, 1_600_000, 3.40, 175),
        new WealthTier(13, "L13", "大土豪", 5, 1_600_000, 2_400_000, 4.00, 188),
        new WealthTier(14, "L14", "神豪", 3, 2_400_000, 3_500_000, 4.80, 205),
        new WealthTier(15, "L15", "顶级土豪", 1, 3_500_000, 5_000_000, 6.00, 240)
    );

    public WealthTier tier(int level) {
        int normalized = Math.max(0, Math.min(TOP_TIER_LEVEL, level));
        return TIERS.get(normalized);
    }

    public WealthTier topTier() {
        return tier(TOP_TIER_LEVEL);
    }

    public WealthTier rollTier(Random random) {
        int totalWeight = TIERS.stream().mapToInt(WealthTier::populationWeight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        int cursor = 0;
        for (WealthTier tier : TIERS) {
            cursor += tier.populationWeight();
            if (roll < cursor) {
                return tier;
            }
        }
        return TIERS.getFirst();
    }

    public WealthTier deterministicTier(String seed) {
        return rollTier(new Random(Objects.hash(seed)));
    }

    public long incomeFor(int tierLevel, Random random) {
        WealthTier tier = tier(tierLevel);
        long spread = Math.max(0, tier.maxIncome() - tier.minIncome());
        double curve = tier.level() >= 10 ? 0.62 : tier.level() >= 6 ? 0.82 : 1.28;
        long extra = Math.round(spread * Math.pow(random.nextDouble(), curve));
        return tier.minIncome() + extra;
    }

    public record WealthTier(
        int level,
        String code,
        String name,
        int populationWeight,
        long minIncome,
        long maxIncome,
        double rechargeMultiplier,
        int priceTolerancePercent
    ) {
    }
}
