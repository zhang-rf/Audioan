package com.audioan.net;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

class SimpleObjectCache {

    protected Map<Class<?>, SoftReference<?>> objectMap = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T put(T object) {
        return (T) objectMap.put(object.getClass(), new SoftReference<>(object));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        SoftReference<T> reference = (SoftReference<T>) objectMap.get(clazz);
        if (reference == null)
            return null;
        return reference.get();
    }
}
