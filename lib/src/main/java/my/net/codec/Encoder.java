package my.net.codec;

import java.nio.ByteBuffer;

public interface Encoder {

    ByteBuffer encode(ByteBuffer buffer, Object object);
}
