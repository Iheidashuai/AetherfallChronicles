package com.mythicrealm.api.gameplay.robot;

public record RobotActionResult(boolean success, String kind, String text) {
    public static RobotActionResult success(String kind, String text) {
        return new RobotActionResult(true, kind, text);
    }

    public static RobotActionResult failure(String kind, String text) {
        return new RobotActionResult(false, kind, text);
    }
}
