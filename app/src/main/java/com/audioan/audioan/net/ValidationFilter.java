package com.audioan.audioan.net;

import com.audioan.net.Filter;

import java.nio.ByteBuffer;

public class ValidationFilter implements Filter {

    private static final String HEAD = "AAN";
    private static final int MIN_PACKET_LENGTH = 4;

    @Override
    public boolean filter(ByteBuffer buffer) {
        return buffer.limit() >= MIN_PACKET_LENGTH && HEAD.equals(new String(buffer.array(), 0, HEAD.length()));
    }
}
