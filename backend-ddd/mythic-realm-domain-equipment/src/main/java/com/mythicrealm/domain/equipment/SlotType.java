package com.mythicrealm.domain.equipment;

/**
 * 装备槽位类型枚举
 */
public enum SlotType {
    WEAPON("weapon", "武器"),
    HELMET("helmet", "头盔"),
    ARMOR("armor", "护甲"),
    LEGS("legs", "腿部"),
    BOOTS("boots", "靴子"),
    GLOVES("gloves", "手套"),
    NECKLACE("necklace", "项链"),
    RING1("ring1", "戒指1"),
    RING2("ring2", "戒指2");

    private final String code;
    private final String displayName;

    SlotType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SlotType fromCode(String code) {
        for (SlotType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的装备槽位类型: " + code);
    }

    public static SlotType fromItemType(String itemType) {
        return switch (itemType) {
            case "weapon" -> WEAPON;
            case "helmet" -> HELMET;
            case "armor" -> ARMOR;
            case "legs" -> LEGS;
            case "boots" -> BOOTS;
            case "gloves" -> GLOVES;
            case "necklace" -> NECKLACE;
            case "ring" -> RING1; // Default to ring1 for ring items
            default -> throw new IllegalArgumentException("物品类型 " + itemType + " 不可装备");
        };
    }

    public boolean isRingSlot() {
        return this == RING1 || this == RING2;
    }
}
