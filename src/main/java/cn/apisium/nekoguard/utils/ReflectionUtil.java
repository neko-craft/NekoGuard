package cn.apisium.nekoguard.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class ReflectionUtil {
    private ReflectionUtil() {}

    /*
     * The server version string to location NMS & OBC classes
     */
    private static String versionString;

    /*
     * Cache of NMS classes that we've searched for
     */
    private static final Map<String, Class<?>> loadedNMSClasses = new HashMap<>();

    /*
     * Cache of OBS classes that we've searched for
     */
    private static final Map<String, Class<?>> loadedOBCClasses = new HashMap<>();

    /*
     * Cache of methods that we've found in particular classes
     */
    private static final Map<Class<?>, Map<String, Method>> loadedMethods = new HashMap<>();

    /*
     * Cache of fields that we've found in particular classes
     */
    private static final Map<Class<?>, Map<String, Field>> loadedFields = new HashMap<>();

    /**
     * Gets the version string for NMS & OBC class paths
     *
     * @return The version string of OBC and NMS packages
     */
    public static String getVersion() {
        if (versionString == null) {
            final String name = Bukkit.getServer().getClass().getPackage().getName();
            versionString = name.substring(name.lastIndexOf('.') + 1) + ".";
        }

        return versionString;
    }

    /**
     * Get an NMS Class
     *
     * @param nmsClassName The name of the class
     * @return The class
     */
    public static Class<?> getNMSClass(final String nmsClassName) {
        if (loadedNMSClasses.containsKey(nmsClassName)) return loadedNMSClasses.get(nmsClassName);

        try {
            final Class<?> clazz = Class.forName("net.minecraft.server." +
                    getVersion() + nmsClassName);
            loadedNMSClasses.put(nmsClassName, clazz);
            return clazz;
        } catch (Throwable t) {
            t.printStackTrace();
            loadedNMSClasses.put(nmsClassName, null);
            return null;
        }
    }

    /**
     * Get a class from the org.bukkit.craftbukkit package
     *
     * @param obcClassName the path to the class
     * @return the found class at the specified path
     */
    public synchronized static Class<?> getOBCClass(final String obcClassName) {
        if (loadedOBCClasses.containsKey(obcClassName)) return loadedOBCClasses.get(obcClassName);

        try {
            final Class<?> clazz = Class.forName("org.bukkit.craftbukkit." +
                    getVersion() + obcClassName);
            loadedOBCClasses.put(obcClassName, clazz);
            return clazz;
        } catch (Throwable t) {
            t.printStackTrace();
            loadedOBCClasses.put(obcClassName, null);
            return null;
        }
    }

    /**
     * Get a Bukkit {@link Player} players NMS playerConnection object
     *
     * @param player The player
     * @return The players connection
     */
    @SuppressWarnings("unused")
    public static Object getConnection(final Player player) {
        final Method getHandleMethod = getMethod(player.getClass(), "getHandle");

        if (getHandleMethod != null) {
            try {
                Object nmsPlayer = getHandleMethod.invoke(player);
                return getField(nmsPlayer.getClass(), "playerConnection").get(nmsPlayer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Get a classes constructor
     *
     * @param clazz  The constructor class
     * @param params The parameters in the constructor
     * @return The constructor object
     */
    @SuppressWarnings("unused")
    public static Constructor<?> getConstructor(final Class<?> clazz, final Class<?>... params) {
        try {
            return clazz.getConstructor(params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Get a method from a class that has the specific paramaters
     *
     * @param clazz      The class we are searching
     * @param methodName The name of the method
     * @param params     Any parameters that the method has
     * @return The method with appropriate paramaters
     */
    public static Method getMethod(final Class<?> clazz, final String methodName, final Class<?>... params) {
        return getMethod(clazz, methodName, false, params);
    }
    public static Method getMethod(final Class<?> clazz, final String methodName, final boolean declared, final Class<?>... params) {
        final Map<String, Method> methods = loadedMethods.computeIfAbsent(clazz, k -> new HashMap<>());

        if (methods.containsKey(methodName)) return methods.get(methodName);

        try {
            final Method method = declared ? clazz.getDeclaredMethod(methodName, params)
                    : clazz.getMethod(methodName, params);
            methods.put(methodName, method);
            if (declared) method.setAccessible(true);
            return method;
        } catch (Exception e) {
            e.printStackTrace();
            methods.put(methodName, null);
            return null;
        }
    }

    /**
     * Get a field with a particular name from a class
     *
     * @param clazz     The class
     * @param fieldName The name of the field
     * @return The field object
     */
    public static Field getField(final Class<?> clazz, final String fieldName) {
        return getField(clazz, fieldName, false);
    }
    public static Field getField(final Class<?> clazz, final String fieldName, final boolean declared) {
        final Map<String, Field> fields = loadedFields.computeIfAbsent(clazz, k -> new HashMap<>());

        if (fields.containsKey(fieldName)) return fields.get(fieldName);

        try {
            final Field field = declared ? clazz.getDeclaredField(fieldName)
                    : clazz.getField(fieldName);
            fields.put(fieldName, field);
            if (declared) field.setAccessible(true);
            return field;
        } catch (Exception e) {
            e.printStackTrace();
            fields.put(fieldName, null);
            return null;
        }
    }
}
