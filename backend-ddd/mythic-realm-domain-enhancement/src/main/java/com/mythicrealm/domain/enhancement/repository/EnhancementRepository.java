package com.mythicrealm.domain.enhancement.repository;

import com.mythicrealm.domain.enhancement.model.Enhancement;

import java.util.Optional;

/**
 * 强化仓储接口
 */
public interface EnhancementRepository {

    /**
     * 根据物品ID查找强化信息
     *
     * @param itemId 物品ID
     * @return 强化信息
     */
    Optional<Enhancement> findByItemId(long itemId);

    /**
     * 保存或更新强化信息
     *
     * @param enhancement 强化对象
     */
    void save(Enhancement enhancement);

    /**
     * 删除强化信息
     *
     * @param itemId 物品ID
     */
    void delete(long itemId);
}
