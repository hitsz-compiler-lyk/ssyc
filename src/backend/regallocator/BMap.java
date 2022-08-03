package backend.regallocator;

import utils.Log;

import java.util.HashMap;
import java.util.Map;

public class BMap<K, V> {
    private final Map<K, V> KVmap = new HashMap<>();
    private final Map<V, K> VKmap = new HashMap<>();

    public void put(K key, V value) {
            Log.ensure(KVmap.containsKey(key) || VKmap.containsKey(value),"Key or value already in BMap.");

        KVmap.put(key, value);
        VKmap.put(value, key);
    }

    public void removeByKey(K key) {
        try {
            VKmap.remove(KVmap.remove(key));
        } catch (NullPointerException e) {
            throw new RuntimeException("Key no found.");
        }
    }

    public void removeByValue(V value) {
        try {
            KVmap.remove(VKmap.remove(value));
        } catch (NullPointerException e) {
            throw new RuntimeException("Value no found.");
        }
    }

    public boolean containsKey(K key) {
        return KVmap.containsKey(key);
    }

    public boolean containsValue(V value) {
        return VKmap.containsKey(value);
    }

    public void replaceByKey(K key, V value) {
        KVmap.replace(key, value);
        VKmap.replace(value, key);
    }

    public void replaceByValue(K key, V value) {
        KVmap.replace(key, value);
        VKmap.replace(value, key);
    }

    public V getByKey(K key) {
        return KVmap.get(key);
    }

    public K getByValue(V value) {
        return VKmap.get(value);
    }
}
