package cn.apisium.nekoguard.utils;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class HashCodeMap <K, V> extends HashMap<K, V> {
    private final HashMap<Integer, K> map = new HashMap<>();

    @Override
    public V put(final K key, final V value) {
        map.put(key == null ? null : key.hashCode(), key);
        return super.put(key, value);
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        map.put(key == null ? null : key.hashCode(), key);
        return super.putIfAbsent(key, value);
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        super.putAll(m);
        m.forEach((k, v) -> map.put(k == null ? null : k.hashCode(), k));
    }

    public V removeByHash(final int code) {
        return remove(getByHash(code));
    }

    @Override
    public V remove(final Object key) {
        map.remove(key == null ? null : key.hashCode());
        return super.remove(key);
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        final boolean success = super.remove(key, value);
        if (success) map.remove(key == null ? null : key.hashCode());
        return success;
    }

    public boolean containsHash(final Integer hash) {
        return map.containsKey(hash);
    }

    public boolean containsHash(final int hash) {
        return map.containsKey(hash);
    }

    @Nullable
    public V getByHash(final int hash) {
        return get(map.get(hash));
    }

    @Nullable
    public V getByHash(final Integer hash) {
        return get(map.get(hash));
    }

    @Nullable
    public V getOrDefaultByHash(final Integer hash, final V defaultValue) {
        final V v = get(map.get(hash));
        return v == null ? defaultValue : v;
    }
}
