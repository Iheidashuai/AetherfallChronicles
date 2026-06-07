package com.mythicrealm.domain.player.model;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 玩家ID值对象
 */
public record PlayerId(Long value) implements ValueObject {

    public PlayerId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("玩家ID必须大于0");
        }
    }

    public static PlayerId of(Long value) {
        return new PlayerId(value);
    }
}
