package com.audioan.audioan.net.packet;

import com.audioan.net.Decoder;
import com.audioan.net.Encoder;
import com.audioan.net.Packet;

import java.nio.ByteBuffer;

@Packet
public class KeepAlive {

    private static Decoder<KeepAlive> decoder = new DecoderImpl();
    private Encoder encoder;

    //region Codec
    private static Decoder<KeepAlive> decoder() {
        return decoder;
    }

    private Encoder encoder() {
        return encoder;
    }
    //endregion

    private static class DecoderImpl implements Decoder<KeepAlive> {

        private static final int PACKET_ID = PacketId.KEEP_ALIVE.ordinal();

        @Override
        public KeepAlive decode(ByteBuffer buffer) {
            return decode(buffer, null);
        }

        @Override
        public KeepAlive decode(ByteBuffer buffer, KeepAlive instance) {
            if (buffer.get() != PACKET_ID)
                return null;

            if (instance == null)
                instance = new KeepAlive();
            return instance;
        }
    }
}
