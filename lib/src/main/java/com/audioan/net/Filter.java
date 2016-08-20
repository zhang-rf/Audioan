package com.audioan.net;

import java.nio.ByteBuffer;

public interface Filter {

    boolean filter(ByteBuffer buffer);
}
