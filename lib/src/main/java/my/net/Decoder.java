package my.net;

import java.nio.ByteBuffer;

public interface Decoder<T> {

    T decode(ByteBuffer buffer);

    T decode(ByteBuffer buffer, T instance);
}
