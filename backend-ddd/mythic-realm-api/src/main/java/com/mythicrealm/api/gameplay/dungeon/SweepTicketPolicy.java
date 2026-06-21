package com.mythicrealm.api.gameplay.dungeon;

public final class SweepTicketPolicy {
    public static final String NORMAL_TICKET_TEMPLATE_ID = "ticket_sweep_normal";
    public static final String SPECIAL_TICKET_TEMPLATE_ID = "ticket_sweep_special";
    public static final int SHORT_SWEEP_TIMES = 10;
    public static final int LONG_SWEEP_TIMES = 50;
    public static final double NORMAL_TICKET_DROP_RATE = 0.35;
    public static final double SPECIAL_TICKET_DROP_RATE = 0.08;
    public static final int MAX_SWEEP_LOOT_ANNOUNCEMENTS = 3;

    private SweepTicketPolicy() {
    }

    public static boolean isSupportedSweepTimes(int times) {
        return times == SHORT_SWEEP_TIMES || times == LONG_SWEEP_TIMES;
    }

    public static String ticketTemplateId(String dungeonId) {
        return DungeonService.isSpecialDungeon(dungeonId) ? SPECIAL_TICKET_TEMPLATE_ID : NORMAL_TICKET_TEMPLATE_ID;
    }

    public static String ticketName(String dungeonId) {
        return DungeonService.isSpecialDungeon(dungeonId) ? "特殊扫荡符" : "普通扫荡符";
    }

    public static double ticketDropRate(String dungeonId) {
        return DungeonService.isSpecialDungeon(dungeonId) ? SPECIAL_TICKET_DROP_RATE : NORMAL_TICKET_DROP_RATE;
    }

    public static int ratingRank(String rating) {
        return switch (rating == null ? "" : rating) {
            case "S" -> 3;
            case "A" -> 2;
            case "B" -> 1;
            default -> 0;
        };
    }
}
