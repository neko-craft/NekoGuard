package cn.apisium.nekoguard.utils;

public final class Pair <K, V> {
    public K key;
    public V value;
    public Pair(final K key, final V value) {
        this.key = key;
        this.value = value;
    }
}
