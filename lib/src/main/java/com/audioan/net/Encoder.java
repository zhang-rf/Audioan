package com.audioan.net;

public interface Encoder {

    int packetLength();

    byte[] encode();

    byte[] encode(byte[] allocated);
}
