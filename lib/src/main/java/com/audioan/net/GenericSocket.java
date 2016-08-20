package com.audioan.net;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GenericSocket {

    protected DatagramSocket socket;
    protected ByteBuffer buffer;
    protected List<Filter> filters = new ArrayList<>();
    protected SimpleObjectCache objectCache = new SimpleObjectCache();

    public GenericSocket(DatagramSocket socket) {
        this(socket, -1);
    }

    public GenericSocket(DatagramSocket socket, int bufferCapacity) {
        reallocateBuffer(bufferCapacity);
        this.socket = socket;
    }

    public DatagramSocket socket() {
        return socket;
    }

    public void reallocateBuffer(int capacity) {
        try {
            if (capacity == -1)
                buffer = ByteBuffer.allocate(socket.getReceiveBufferSize());
            else
                buffer = ByteBuffer.allocate(capacity);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDestination(String host, int port) {
        if (socket.isConnected())
            throw new UnsupportedOperationException("socket is connected");

        try {
            UdpPacket.get().setAddress(InetAddress.getByName(host));
            UdpPacket.get().setPort(port);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Filter> filters() {
        return filters;
    }

    public void send(byte[] data, int offset, int length) {
        try {
            socket.send(UdpPacket.get(data, offset, length));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] receive() {
        DatagramPacket udpPacket = UdpPacket.get(buffer.array(), 0, buffer.capacity());
        try {
            socket.receive(udpPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Arrays.copyOf(buffer.array(), udpPacket.getLength());
    }

    public <T> void push(T packet) {
        Packet packetAnnotation = packetAnnotation(packet.getClass());
        Encoder encoder = encoder(packet, packetAnnotation.encoder());
        encoder.encode(buffer.array());
        send(buffer.array(), 0, encoder.packetLength());
    }

    public <T> T pull(Class<T> clazz) {
        return pull(clazz, false);
    }

    @SuppressWarnings("unchecked")
    public <T> T pull(Class<T> clazz, boolean cache) {
        return (T) pullWithin(Collections.singletonList(clazz), cache);
    }

    public Object pullWithin(List<? extends Class<?>> classes) {
        return pullWithin(classes, false);
    }

    @SuppressWarnings("unchecked")
    public Object pullWithin(List<? extends Class<?>> classes, boolean cache) {
        DatagramPacket udpPacket = UdpPacket.get(buffer.array(), 0, buffer.capacity());
        try {
            socket.receive(udpPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        buffer.clear();
        buffer.limit(udpPacket.getLength());
        for (Filter filter : filters) {
            if (!filter.filter(buffer))
                throw new IllegalStateException();
        }

        buffer.mark();
        for (Class<?> clazz : classes) {
            buffer.reset();
            Packet packetAnnotation = packetAnnotation(clazz);
            Decoder decoder = decoder(clazz, packetAnnotation.decoder());

            Object instance = null;
            if (cache)
                instance = objectCache.get(clazz);

            if (!cache || instance == null) {
                instance = decoder.decode(buffer);
                if (cache)
                    objectCache.put(instance);
            } else
                decoder.decode(buffer, instance);

            if (instance != null)
                return instance;
        }
        return null;
    }

    //region Private Methods
    private Packet packetAnnotation(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Packet.class))
            throw new IllegalArgumentException("missing annotation: Packet");
        return clazz.getAnnotation(Packet.class);
    }

    @SuppressWarnings("unchecked")
    private <T> Encoder encoder(T packet, String encoder) {
        try {
            Method method = packet.getClass().getDeclaredMethod(encoder);
            method.setAccessible(true);
            return (Encoder) method.invoke(packet);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Decoder<T> decoder(Class<T> clazz, String decoder) {
        try {
            Method method = clazz.getDeclaredMethod(decoder);
            method.setAccessible(true);
            return (Decoder<T>) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    //endregion

    protected final static class UdpPacket {

        private static DatagramPacket packet = new DatagramPacket(new byte[0], 0);

        public static DatagramPacket get() {
            return packet;
        }

        public static DatagramPacket get(byte[] data, int offset, int length) {
            packet.setData(data, offset, length);
            return packet;
        }
    }
}
