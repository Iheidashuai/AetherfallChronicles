package com.mythicrealm.domain.equipment.config;

import com.mythicrealm.domain.equipment.application.EquipmentApplicationService;
import com.mythicrealm.domain.equipment.repository.EquipmentRepository;
import com.mythicrealm.domain.equipment.service.CombatPowerCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 装备领域配置
 */
@Configuration
@ComponentScan(basePackages = "com.mythicrealm.domain.equipment")
public class EquipmentConfiguration {

    @Bean
    public CombatPowerCalculator combatPowerCalculator() {
        return new CombatPowerCalculator();
    }

    @Bean
    public EquipmentApplicationService equipmentApplicationService(
        EquipmentRepository equipmentRepository,
        CombatPowerCalculator combatPowerCalculator
    ) {
        return new EquipmentApplicationService(equipmentRepository, combatPowerCalculator);
    }
}
