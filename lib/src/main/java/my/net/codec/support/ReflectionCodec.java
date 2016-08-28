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
    private Map<String, Integer> hashCache = new HashMap<>();

    @Override
    public ByteBuffer encode(ByteBuffer buffer, Object object) {
        Class<?> clazz = object.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            try {
                String name = field.getName();
                Method method = clazz.getMethod(getter(name));
                method.setAccessible(true);

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
                    else if (type == String.class) {
                        byte[] stringBytes = ((String) value).getBytes("UTF-8");
                        int length = stringBytes.length;
                        if (length > Short.MAX_VALUE)
                            buffer.putInt(-length);
                        else
                            buffer.putShort((short) length);
                        buffer.put(stringBytes);
                    } else {

                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        buffer.putShort((short) 0);

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
        try {
            Class<?> clazz = classes.get(0);
            Object instance = clazz.newInstance();
            Field[] fields = clazz.getDeclaredFields();
            short hash = Short.MAX_VALUE;
            while (hash != 0 && buffer.hasRemaining()) {
                hash = buffer.getShort();

                for (Field field : fields) {
                    if (hash(field.getName()) == hash) {
                        field.setAccessible(true);
                        Class<?> type = field.getType();
                        if (type == byte.class)
                            field.set(instance, buffer.get());
                        else if (type == char.class)
                            field.set(instance, buffer.getChar());
                        else if (type == short.class)
                            field.set(instance, buffer.getShort());
                        else if (type == int.class)
                            field.set(instance, buffer.getInt());
                        else if (type == long.class)
                            field.set(instance, buffer.getLong());
                        else if (type == float.class)
                            field.set(instance, buffer.getFloat());
                        else if (type == double.class)
                            field.set(instance, buffer.getDouble());
                        else if (type == String.class) {
                            int length = buffer.getShort();
                            if (length < 0) {
                                buffer.position(buffer.position() - 2);
                                length = -buffer.getInt();
                            }
                            char[] chars = new char[length];
                            buffer.asCharBuffer().get(chars);
                            field.set(instance, new String(buffer.array(), buffer.position(), length, "UTF-8"));
                            buffer.position(buffer.position() + length);
                        } else {

                        }
                        break;
                    }
                }
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        if (hashCache.get(name) != null)
            return hashCache.get(name).shortValue();
        else {
            int h = 0;
            for (int i = 0, length = name.length(); i < length; i++)
                h = 31 * h + name.charAt(i);
            h %= Short.MAX_VALUE;

            hashCache.put(name, h);
            return (short) h;
        }
    }
}
