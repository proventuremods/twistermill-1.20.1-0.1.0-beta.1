package com.proventure.twistermill.util;

import java.lang.reflect.Field;

public final class CreateWindmillReflectionCleaner {

    private CreateWindmillReflectionCleaner() {
    }

    public static void zeroOutCreateWindmillContribution(Object target) {
        try {
            zeroIntFieldIfPresent(target, "sailBlockCount");
            zeroIntFieldIfPresent(target, "sailBlocks");
            zeroIntFieldIfPresent(target, "sails");
            zeroIntFieldIfPresent(target, "sailCount");
            zeroIntFieldIfPresent(target, "numSails");
            zeroIntFieldIfPresent(target, "windmillSails");

            zeroFloatFieldIfPresent(target, "windmillEfficiency");
            zeroFloatFieldIfPresent(target, "efficiency");
            zeroFloatFieldIfPresent(target, "sailEfficiency");
            zeroFloatFieldIfPresent(target, "windMultiplier");
            zeroFloatFieldIfPresent(target, "sailMultiplier");

            zeroFloatFieldIfPresent(target, "windmillCapacity");
            zeroFloatFieldIfPresent(target, "windmillStressCapacity");
            zeroFloatFieldIfPresent(target, "windmillStress");
            zeroFloatFieldIfPresent(target, "cachedStressCapacity");
            zeroFloatFieldIfPresent(target, "cachedCapacity");
        } catch (Throwable ignored) {
        }
    }

    private static void zeroIntFieldIfPresent(Object target, String fieldName) {
        Field f = findFieldInHierarchy(target.getClass(), fieldName);
        if (f == null) {
            return;
        }

        try {
            f.setAccessible(true);
            if (f.getType() == int.class) {
                f.setInt(target, 0);
            } else if (f.getType() == Integer.class) {
                f.set(target, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void zeroFloatFieldIfPresent(Object target, String fieldName) {
        Field f = findFieldInHierarchy(target.getClass(), fieldName);
        if (f == null) {
            return;
        }

        try {
            f.setAccessible(true);
            if (f.getType() == float.class) {
                f.setFloat(target, 0.0F);
            } else if (f.getType() == Float.class) {
                f.set(target, 0.0F);
            } else if (f.getType() == double.class) {
                f.setDouble(target, 0.0D);
            } else if (f.getType() == Double.class) {
                f.set(target, 0.0D);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findFieldInHierarchy(Class<?> start, String name) {
        Class<?> c = start;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
