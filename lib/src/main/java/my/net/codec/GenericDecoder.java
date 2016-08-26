package my.net.codec;

import java.nio.ByteBuffer;
import java.util.List;

public interface GenericDecoder extends Decoder {

    List<Class> classes();

    Object decode(ByteBuffer buffer);
}
