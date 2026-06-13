package com.mythicrealm.api.gameplay.robot;

public record RobotActionScore(double value, String reason) {
    public static RobotActionScore zero(String reason) {
        return new RobotActionScore(0, reason);
    }
}
