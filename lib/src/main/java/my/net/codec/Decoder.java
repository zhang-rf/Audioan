package my.net.codec;

import java.nio.ByteBuffer;

public interface Decoder<T> {

    T decode(ByteBuffer buffer, Class<T> clazz);

    T decode(ByteBuffer buffer, T instance);
}
