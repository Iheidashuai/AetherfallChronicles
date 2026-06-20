package com.mythicrealm.api.gameplay.robot;

/**
 * Response curves for utility scoring (a.k.a. considerations).
 *
 * <p>Real people don't react linearly to a stat: stamina at 90% vs 60% feels very
 * different, and a power gap only becomes urgent past a threshold. These pure
 * functions map a raw input to a normalised 0..1 urgency so {@code score()} can
 * shape "how much do I want this" instead of clamping a straight line. Multiply
 * the 0..1 result by the weight you want it to contribute.
 */
public final class RobotResponseCurves {
    private RobotResponseCurves() {
    }

    /** Clamp a raw value into 0..1 across [min, max] (linear). */
    public static double linear(double value, double min, double max) {
        if (max <= min) {
            return value >= max ? 1.0 : 0.0;
        }
        return clamp01((value - min) / (max - min));
    }

    /**
     * Logistic S-curve centred on {@code midpoint}. {@code steepness} controls how
     * sharply urgency rises around the midpoint. Returns 0..1.
     */
    public static double logistic(double value, double midpoint, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * (value - midpoint)));
    }

    /**
     * Inverse logistic: urgency is high when the value is LOW and falls off past
     * the midpoint. Useful for "I'm running out of X" considerations.
     */
    public static double inverseLogistic(double value, double midpoint, double steepness) {
        return 1.0 - logistic(value, midpoint, steepness);
    }

    /** Quadratic ease-in over [min, max]: small inputs barely matter, large ones dominate. */
    public static double quadratic(double value, double min, double max) {
        double t = linear(value, min, max);
        return t * t;
    }

    /** Soft threshold: ~0 below {@code threshold}, ramping to 1 across {@code width}. */
    public static double threshold(double value, double threshold, double width) {
        if (width <= 0) {
            return value >= threshold ? 1.0 : 0.0;
        }
        return clamp01((value - threshold) / width);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
