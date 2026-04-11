package com.proventure.twistermill.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared reflection-based utility methods used by both WindRotoBlockEntity
 * and WindRotoVerticalBlockEntity. Extracted to eliminate code duplication.
 * <p>
 * Covers:
 * - Valkyrien Skies 2 world-position lookups (reflection-based, optional dependency)
 * - Create windmill field zeroing (to suppress parent-class sail logic)
 * - Generic reflection helpers (field hierarchy search, numeric field zeroing)
 */
public final class WindRotoReflectionHelper {

    private static final String VS_GAME_UTILS_CLASS_NAME = "org.valkyrienskies.mod.common.VSGameUtilsKt";

    private static final String[] CREATE_ZERO_INT_FIELDS = new String[]{
            "sailBlockCount",
            "sailBlocks",
            "sails",
            "sailCount",
            "numSails",
            "windmillSails"
    };

    private static final String[] CREATE_ZERO_FLOAT_FIELDS = new String[]{
            "windmillEfficiency",
            "efficiency",
            "sailEfficiency",
            "windMultiplier",
            "sailMultiplier",
            "windmillCapacity",
            "windmillStressCapacity",
            "windmillStress",
            "cachedStressCapacity",
            "cachedCapacity"
    };

    private static final Object VS_INIT_LOCK = new Object();

    private static volatile boolean vsReflectionInitialized = false;
    private static volatile boolean vsReflectionAvailable = false;

    @Nullable
    private static volatile Class<?> vsGameUtilsClass;

    @Nullable
    private static volatile Method getShipManagingPosMethod;

    private static final Map<Class<?>, Method> TO_WORLD_COORDINATES_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> TO_WORLD_COORDINATES_METHOD_MISS = ConcurrentHashMap.newKeySet();

    private static final Map<Class<?>, Method> GET_TRANSFORM_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> GET_TRANSFORM_METHOD_MISS = ConcurrentHashMap.newKeySet();

    private static final Map<Class<?>, Method> GET_SHIP_TO_WORLD_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> GET_SHIP_TO_WORLD_METHOD_MISS = ConcurrentHashMap.newKeySet();

    private static final Map<Class<?>, Method> TRANSFORM_POSITION_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> TRANSFORM_POSITION_METHOD_MISS = ConcurrentHashMap.newKeySet();

    private record FieldCacheKey(Class<?> owner, String name) {
    }

    private static final Map<FieldCacheKey, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Set<FieldCacheKey> MISSING_FIELD_CACHE = ConcurrentHashMap.newKeySet();

    private WindRotoReflectionHelper() {
    }

    // ── Valkyrien Skies 2 integration ──────────────────────────────────

    /**
     * Attempts to convert a ship-local BlockPos to a world BlockPos via VS2 reflection.
     * Falls back to {@link #getWorldCenterFallbackTransform} if direct methods are unavailable.
     */
    public static BlockPos getWorldBlockPos(Level level, BlockPos worldPosition) {
        if (level == null) {
            return worldPosition;
        }

        Object ship = getShipManagingPos(level, worldPosition);
        if (ship != null) {
            Method toWorldCoordinates = findToWorldCoordinatesMethod(ship.getClass());
            if (toWorldCoordinates != null) {
                Object out = invokeQuietly(toWorldCoordinates, null, ship, worldPosition);
                if (out != null) {
                    if (out instanceof Vector3d vec) {
                        return BlockPos.containing(vec.x, vec.y, vec.z);
                    }

                    try {
                        double wx = readJomlDouble(out, "x");
                        double wy = readJomlDouble(out, "y");
                        double wz = readJomlDouble(out, "z");
                        return BlockPos.containing(wx, wy, wz);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        Vec3 w = getWorldCenterFallbackTransform(level, worldPosition);
        return BlockPos.containing(w.x, w.y, w.z);
    }

    /**
     * Transforms a ship-local position to world coordinates via VS2 ship transform (reflection).
     * Returns the block center (+ 0.5) in world space, or the local center if VS2 is absent.
     */
    public static Vec3 getWorldCenterFallbackTransform(Level level, BlockPos worldPosition) {
        Vec3 localCenter = new Vec3(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5
        );

        if (level == null) {
            return localCenter;
        }

        Object ship = getShipManagingPos(level, worldPosition);
        if (ship == null) {
            return localCenter;
        }

        Method toWorldCoordinates = findToWorldCoordinatesMethod(ship.getClass());
        if (toWorldCoordinates != null) {
            Object out = invokeQuietly(toWorldCoordinates, null, ship, worldPosition);
            if (out != null) {
                if (out instanceof Vector3d vec) {
                    return new Vec3(vec.x, vec.y, vec.z);
                }

                try {
                    double wx = readJomlDouble(out, "x");
                    double wy = readJomlDouble(out, "y");
                    double wz = readJomlDouble(out, "z");
                    return new Vec3(wx, wy, wz);
                } catch (Throwable ignored) {
                }
            }
        }

        Method getTransform = findCachedNoArgMethod(
                ship.getClass(),
                "getTransform",
                GET_TRANSFORM_METHOD_CACHE,
                GET_TRANSFORM_METHOD_MISS
        );
        if (getTransform == null) {
            return localCenter;
        }

        Object transform = invokeQuietly(getTransform, ship);
        if (transform == null) {
            return localCenter;
        }

        Method getShipToWorld = findCachedNoArgMethod(
                transform.getClass(),
                "getShipToWorld",
                GET_SHIP_TO_WORLD_METHOD_CACHE,
                GET_SHIP_TO_WORLD_METHOD_MISS
        );
        if (getShipToWorld == null) {
            return localCenter;
        }

        Object shipToWorld = invokeQuietly(getShipToWorld, transform);
        if (shipToWorld == null) {
            return localCenter;
        }

        Method transformPosition = findTransformPositionMethod(shipToWorld.getClass());
        if (transformPosition == null) {
            return localCenter;
        }

        Vector3d jomlPos = new Vector3d(localCenter.x, localCenter.y, localCenter.z);
        Object out = invokeQuietly(transformPosition, shipToWorld, jomlPos);
        if (out == null) {
            return localCenter;
        }

        try {
            double wx = readJomlDouble(out, "x");
            double wy = readJomlDouble(out, "y");
            double wz = readJomlDouble(out, "z");
            return new Vec3(wx, wy, wz);
        } catch (Throwable ignored) {
            return localCenter;
        }
    }

    @Nullable
    private static Object getShipManagingPos(Level level, BlockPos worldPosition) {
        if (!isVsReflectionAvailable() || getShipManagingPosMethod == null) {
            return null;
        }
        return invokeQuietly(getShipManagingPosMethod, null, level, worldPosition);
    }

    private static boolean isVsReflectionAvailable() {
        if (vsReflectionInitialized) {
            return vsReflectionAvailable;
        }

        synchronized (VS_INIT_LOCK) {
            if (vsReflectionInitialized) {
                return vsReflectionAvailable;
            }

            try {
                Class<?> resolvedVsGameUtilsClass = Class.forName(VS_GAME_UTILS_CLASS_NAME);
                Method resolvedGetShipManagingPos = resolvedVsGameUtilsClass.getMethod(
                        "getShipManagingPos",
                        Level.class,
                        BlockPos.class
                );
                safeSetAccessible(resolvedGetShipManagingPos);

                vsGameUtilsClass = resolvedVsGameUtilsClass;
                getShipManagingPosMethod = resolvedGetShipManagingPos;
                vsReflectionAvailable = true;
            } catch (Throwable ignored) {
                vsGameUtilsClass = null;
                getShipManagingPosMethod = null;
                vsReflectionAvailable = false;
            }

            vsReflectionInitialized = true;
            return vsReflectionAvailable;
        }
    }

    @Nullable
    private static Method findToWorldCoordinatesMethod(Class<?> shipClass) {
        Method cached = TO_WORLD_COORDINATES_METHOD_CACHE.get(shipClass);
        if (cached != null) {
            return cached;
        }
        if (TO_WORLD_COORDINATES_METHOD_MISS.contains(shipClass)) {
            return null;
        }

        if (!isVsReflectionAvailable()) {
            return null;
        }

        Class<?> localVsGameUtilsClass = vsGameUtilsClass;
        if (localVsGameUtilsClass == null) {
            return null;
        }

        try {
            Method exact = localVsGameUtilsClass.getMethod("toWorldCoordinates", shipClass, BlockPos.class);
            safeSetAccessible(exact);
            TO_WORLD_COORDINATES_METHOD_CACHE.put(shipClass, exact);
            return exact;
        } catch (Throwable ignored) {
        }

        for (Method method : localVsGameUtilsClass.getMethods()) {
            if (!method.getName().equals("toWorldCoordinates")) {
                continue;
            }

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 2) {
                continue;
            }
            if (!params[0].isAssignableFrom(shipClass)) {
                continue;
            }
            if (params[1] != BlockPos.class) {
                continue;
            }

            safeSetAccessible(method);
            TO_WORLD_COORDINATES_METHOD_CACHE.put(shipClass, method);
            return method;
        }

        TO_WORLD_COORDINATES_METHOD_MISS.add(shipClass);
        return null;
    }

    @Nullable
    private static Method findCachedNoArgMethod(
            Class<?> owner,
            String methodName,
            Map<Class<?>, Method> cache,
            Set<Class<?>> missCache
    ) {
        Method cached = cache.get(owner);
        if (cached != null) {
            return cached;
        }
        if (missCache.contains(owner)) {
            return null;
        }

        try {
            Method exact = owner.getMethod(methodName);
            safeSetAccessible(exact);
            cache.put(owner, exact);
            return exact;
        } catch (Throwable ignored) {
        }

        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 0) {
                continue;
            }
            safeSetAccessible(method);
            cache.put(owner, method);
            return method;
        }

        missCache.add(owner);
        return null;
    }

    @Nullable
    private static Method findTransformPositionMethod(Class<?> owner) {
        Method cached = TRANSFORM_POSITION_METHOD_CACHE.get(owner);
        if (cached != null) {
            return cached;
        }
        if (TRANSFORM_POSITION_METHOD_MISS.contains(owner)) {
            return null;
        }

        try {
            Method exact = owner.getMethod("transformPosition", Vector3d.class);
            safeSetAccessible(exact);
            TRANSFORM_POSITION_METHOD_CACHE.put(owner, exact);
            return exact;
        } catch (Throwable ignored) {
        }

        for (Method method : owner.getMethods()) {
            if (!method.getName().equals("transformPosition") || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0] != Vector3d.class) {
                continue;
            }
            safeSetAccessible(method);
            TRANSFORM_POSITION_METHOD_CACHE.put(owner, method);
            return method;
        }

        TRANSFORM_POSITION_METHOD_MISS.add(owner);
        return null;
    }

    // ── Create windmill field zeroing ──────────────────────────────────

    /**
     * Zeros out all known Create windmill sail/efficiency/capacity fields on the target object
     * via reflection, so the parent MechanicalBearingBlockEntity does not contribute its own
     * wind-based stress capacity.
     */
    public static void zeroOutCreateWindmillContribution(Object target) {
        if (target == null) {
            return;
        }

        try {
            for (String fieldName : CREATE_ZERO_INT_FIELDS) {
                zeroIntFieldIfPresent(target, fieldName);
            }

            for (String fieldName : CREATE_ZERO_FLOAT_FIELDS) {
                zeroFloatFieldIfPresent(target, fieldName);
            }
        } catch (Throwable ignored) {
        }
    }

    // ── Generic reflection helpers ─────────────────────────────────────

    public static double readJomlDouble(Object obj, String fieldName) throws Exception {
        Field f = findFieldInHierarchy(obj.getClass(), fieldName);
        if (f == null) {
            throw new NoSuchFieldException(fieldName);
        }

        Object v = f.get(obj);
        if (v instanceof Double d) {
            return d;
        }
        if (v instanceof Float fl) {
            return fl.doubleValue();
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }

        throw new IllegalStateException("Field " + fieldName + " is not numeric");
    }

    public static void zeroIntFieldIfPresent(Object target, String fieldName) {
        Field f = findFieldInHierarchy(target.getClass(), fieldName);
        if (f == null) {
            return;
        }

        try {
            if (f.getType() == int.class) {
                if (f.getInt(target) != 0) {
                    f.setInt(target, 0);
                }
            } else if (f.getType() == Integer.class) {
                Object value = f.get(target);
                if (!(value instanceof Integer integer) || integer != 0) {
                    f.set(target, 0);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void zeroFloatFieldIfPresent(Object target, String fieldName) {
        Field f = findFieldInHierarchy(target.getClass(), fieldName);
        if (f == null) {
            return;
        }

        try {
            if (f.getType() == float.class) {
                if (f.getFloat(target) != 0.0F) {
                    f.setFloat(target, 0.0F);
                }
            } else if (f.getType() == Float.class) {
                Object value = f.get(target);
                if (!(value instanceof Float fl) || fl != 0.0F) {
                    f.set(target, 0.0F);
                }
            } else if (f.getType() == double.class) {
                if (f.getDouble(target) != 0.0D) {
                    f.setDouble(target, 0.0D);
                }
            } else if (f.getType() == Double.class) {
                Object value = f.get(target);
                if (!(value instanceof Double d) || d != 0.0D) {
                    f.set(target, 0.0D);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    public static Field findFieldInHierarchy(Class<?> start, String name) {
        FieldCacheKey key = new FieldCacheKey(start, name);

        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_FIELD_CACHE.contains(key)) {
            return null;
        }

        Class<?> c = start;
        while (c != null) {
            try {
                Field field = c.getDeclaredField(name);
                safeSetAccessible(field);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }

        MISSING_FIELD_CACHE.add(key);
        return null;
    }

    @Nullable
    private static Object invokeQuietly(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void safeSetAccessible(Field field) {
        try {
            field.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    private static void safeSetAccessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }
}