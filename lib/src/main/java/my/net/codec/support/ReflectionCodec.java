package my.net.codec.support;

import my.net.codec.Encoder;
import my.net.codec.GenericDecoder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReflectionCodec implements Encoder, GenericDecoder {

    protected List<Class> classes = new ArrayList<>();

    private StringBuilder stringBuilder = new StringBuilder();
    private Map<String, Short> hashCache = new HashMap<>();

    @Override
    public ByteBuffer encode(ByteBuffer buffer, Object object) {
        Class<?> clazz = object.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            try {
                String name = field.getName();
                Method method = clazz.getMethod(getter(name));

                try {
                    Object value = method.invoke(object);
                    if (value == null)
                        continue;

                    Class<?> type = field.getType();
                    short hash = hash(name);

                    buffer.putShort(hash);
                    if (type == byte.class)
                        buffer.put((byte) value);
                    else if (type == char.class)
                        buffer.putChar((char) value);
                    else if (type == short.class)
                        buffer.putShort((short) value);
                    else if (type == int.class)
                        buffer.putInt((int) value);
                    else if (type == long.class)
                        buffer.putLong((long) value);
                    else if (type == float.class)
                        buffer.putFloat((float) value);
                    else if (type == double.class)
                        buffer.putDouble((double) value);
                    else {

                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (NoSuchMethodException ignored) {
            }
        }

        return buffer;
    }

    @Override
    public Object decode(ByteBuffer buffer, Class clazz) {
        return null;
    }

    @Override
    public Object decode(ByteBuffer buffer, Object instance) {
        return null;
    }

    @Override
    public List<Class> classes() {
        return classes;
    }

    @Override
    public Object decode(ByteBuffer buffer) {
        return null;
    }

    private String getter(String field) {
        try {
            stringBuilder.append("get")
                    .append(Character.toUpperCase(field.charAt(0)))
                    .append(field.toCharArray(), 1, field.length() - 1);
            return stringBuilder.toString();
        } finally {
            stringBuilder.setLength(0);
        }
    }

    public short hash(String name) {
        int h = hashCache.get(name);
        if (h == 0) {
            for (int i = 0, length = name.length(); i < length; i++)
                h = 31 * h + name.charAt(i);
            h %= Short.MAX_VALUE;

            hashCache.put(name, (short) h);
        }
        return (short) h;
    }
}
