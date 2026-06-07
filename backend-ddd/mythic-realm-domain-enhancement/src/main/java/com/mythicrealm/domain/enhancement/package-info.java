/**
 * 装备强化领域模块
 *
 * <p>本模块实现装备强化系统的完整 DDD 架构，从 InventoryService 中拆分出强化相关逻辑。
 *
 * <h2>领域层 (Domain Layer)</h2>
 * <ul>
 *   <li><b>聚合根</b>: {@link com.mythicrealm.domain.enhancement.model.Enhancement} - 强化聚合根</li>
 *   <li><b>值对象</b>:
 *     <ul>
 *       <li>{@link com.mythicrealm.domain.enhancement.valueobject.EnhancementLevel} - 强化等级</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.valueobject.EnhancementLuck} - 强化幸运值</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.valueobject.EnhancementResult} - 强化结果</li>
 *     </ul>
 *   </li>
 *   <li><b>领域服务</b>:
 *     <ul>
 *       <li>{@link com.mythicrealm.domain.enhancement.service.EnhancementStrategy} - 强化策略接口</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.service.SafeEnhancementStrategy} - 安全强化策略 (1-6级)</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.service.NormalEnhancementStrategy} - 普通强化策略 (7-12级)</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.service.RiskyEnhancementStrategy} - 危险强化策略 (13-15级)</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.service.EnhancementCostCalculator} - 成本计算器</li>
 *     </ul>
 *   </li>
 *   <li><b>仓储接口</b>: {@link com.mythicrealm.domain.enhancement.repository.EnhancementRepository}</li>
 *   <li><b>领域事件</b>:
 *     <ul>
 *       <li>{@link com.mythicrealm.domain.enhancement.event.EnhancementAttemptedEvent} - 强化尝试事件</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.event.EnhancementSucceededEvent} - 强化成功事件</li>
 *       <li>{@link com.mythicrealm.domain.enhancement.event.EnhancementFailedEvent} - 强化失败事件</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>应用层 (Application Layer)</h2>
 * <ul>
 *   <li>{@link com.mythicrealm.domain.enhancement.application.EnhancementApplicationService} - 强化应用服务</li>
 *   <li>{@link com.mythicrealm.domain.enhancement.application.command.EnhanceCommand} - 强化命令</li>
 * </ul>
 *
 * <h2>基础设施层 (Infrastructure Layer)</h2>
 * <ul>
 *   <li>{@link com.mythicrealm.domain.enhancement.infrastructure.persistence.EnhancementRepositoryImpl} - 仓储实现</li>
 * </ul>
 *
 * <h2>接口层 (Interface Layer)</h2>
 * <ul>
 *   <li>{@link com.mythicrealm.domain.enhancement.interfaces.EnhancementController} - REST 控制器</li>
 *   <li>{@link com.mythicrealm.domain.enhancement.interfaces.dto.EnhanceResultDTO} - 强化结果 DTO</li>
 * </ul>
 *
 * <h2>强化规则</h2>
 * <h3>成功率</h3>
 * <ul>
 *   <li>1-3级: 100% 成功率</li>
 *   <li>4-6级: 80% 成功率</li>
 *   <li>7-9级: 60% 成功率</li>
 *   <li>10-12级: 40% 成功率</li>
 *   <li>13-15级: 20% 成功率</li>
 * </ul>
 *
 * <h3>失败惩罚</h3>
 * <ul>
 *   <li>1-6级: 失败不掉级</li>
 *   <li>7-12级: 失败掉1级</li>
 *   <li>13-15级: 失败掉2级</li>
 * </ul>
 *
 * <h3>幸运值</h3>
 * <ul>
 *   <li>每次失败增加1点幸运值</li>
 *   <li>每点幸运值增加5%成功率</li>
 *   <li>强化成功后幸运值清零</li>
 * </ul>
 *
 * <h3>成本公式</h3>
 * <pre>
 * 成本 = 装备等级² × 目标强化等级 × 10
 * </pre>
 *
 * @author Mythic Realm Team
 * @version 1.0.0
 * @since 2026-06-07
 */
package com.mythicrealm.domain.enhancement;
