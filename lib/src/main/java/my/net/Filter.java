package my.net;

import java.nio.ByteBuffer;

public interface Filter {

    boolean filter(ByteBuffer buffer);
}
