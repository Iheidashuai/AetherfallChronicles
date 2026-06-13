package com.mythicrealm.domain.equipment.interfaces;

import com.mythicrealm.domain.equipment.CombatPower;
import com.mythicrealm.domain.equipment.SlotType;
import com.mythicrealm.domain.equipment.application.EquipmentApplicationService;
import com.mythicrealm.domain.equipment.application.command.EquipCommand;
import com.mythicrealm.domain.equipment.application.command.UnequipCommand;
import com.mythicrealm.domain.equipment.interfaces.dto.EquipmentDTO;
import com.mythicrealm.domain.equipment.service.CombatPowerCalculator;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 装备控制器
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {
    private final EquipmentApplicationService equipmentApplicationService;

    public EquipmentController(EquipmentApplicationService equipmentApplicationService) {
        this.equipmentApplicationService = equipmentApplicationService;
    }

    /**
     * 装备物品
     */
    @PostMapping("/equip")
    public EquipmentDTO.EquipResponse equip(
        @RequestAttribute("playerId") long playerId,
        @RequestBody EquipmentDTO.EquipRequest request
    ) {
        try {
            EquipCommand command = new EquipCommand(playerId, request.itemId());
            EquipmentApplicationService.EquipResult result = equipmentApplicationService.equip(command);

            Map<SlotType, Long> equippedItems = equipmentApplicationService.getEquippedItems(playerId);

            String message = result.hasReplacedItem()
                ? "装备成功,替换了旧装备"
                : "装备成功";

            return new EquipmentDTO.EquipResponse(
                true,
                message,
                result.replacedItemId(),
                EquipmentDTO.toSlotMap(equippedItems)
            );
        } catch (Exception e) {
            return new EquipmentDTO.EquipResponse(
                false,
                "装备失败: " + e.getMessage(),
                null,
                Map.of()
            );
        }
    }

    /**
     * 卸下装备
     */
    @PostMapping("/unequip")
    public EquipmentDTO.UnequipResponse unequip(
        @RequestAttribute("playerId") long playerId,
        @RequestBody EquipmentDTO.UnequipRequest request
    ) {
        try {
            UnequipCommand command = new UnequipCommand(playerId, request.itemId());
            EquipmentApplicationService.UnequipResult result = equipmentApplicationService.unequip(command);

            Map<SlotType, Long> equippedItems = equipmentApplicationService.getEquippedItems(playerId);

            return new EquipmentDTO.UnequipResponse(
                true,
                "卸下装备成功",
                result.slotType().getCode(),
                EquipmentDTO.toSlotMap(equippedItems)
            );
        } catch (Exception e) {
            return new EquipmentDTO.UnequipResponse(
                false,
                "卸下装备失败: " + e.getMessage(),
                null,
                Map.of()
            );
        }
    }

    /**
     * 获取已装备的物品
     */
    @GetMapping("/equipped")
    public EquipmentDTO.EquippedItemsResponse getEquippedItems(
        @RequestAttribute("playerId") long playerId,
        @RequestParam(name = "level", required = false) Integer level,
        @RequestParam(name = "attack", required = false) Integer attack,
        @RequestParam(name = "defense", required = false) Integer defense,
        @RequestParam(name = "maxHp", required = false) Integer maxHp,
        @RequestParam(name = "maxMp", required = false) Integer maxMp,
        @RequestParam(name = "agility", required = false) Integer agility
    ) {
        Map<SlotType, Long> equippedItems = equipmentApplicationService.getEquippedItems(playerId);

        // 如果提供了玩家属性,计算战斗力
        int combatPower = 0;
        if (level != null && attack != null && defense != null &&
            maxHp != null && maxMp != null && agility != null) {
            CombatPowerCalculator.PlayerStats playerStats =
                new CombatPowerCalculator.PlayerStats(level, attack, defense, maxHp, maxMp, agility);
            CombatPower power = equipmentApplicationService.calculateCombatPower(playerId, playerStats);
            combatPower = power.value();
        }

        return new EquipmentDTO.EquippedItemsResponse(
            EquipmentDTO.toSlotMap(equippedItems),
            combatPower
        );
    }
}
