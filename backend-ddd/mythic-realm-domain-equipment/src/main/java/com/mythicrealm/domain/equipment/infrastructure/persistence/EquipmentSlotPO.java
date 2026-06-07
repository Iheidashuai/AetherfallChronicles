package com.mythicrealm.domain.equipment.infrastructure.persistence;

/**
 * 装备槽位持久化对象
 */
public class EquipmentSlotPO {
    private Long id;
    private Long playerId;
    private String slotName;
    private Long itemId;

    public EquipmentSlotPO() {
    }

    public EquipmentSlotPO(Long playerId, String slotName, Long itemId) {
        this.playerId = playerId;
        this.slotName = slotName;
        this.itemId = itemId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public String getSlotName() {
        return slotName;
    }

    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }
}
