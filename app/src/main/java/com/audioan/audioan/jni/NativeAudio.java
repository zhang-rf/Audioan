package com.audioan.audioan.jni;

public final class NativeAudio {

    static {
        System.loadLibrary("NativeAudio");
    }

    public static native void createEngine();

    public static native void createBufferQueueAudioPlayer(int sampleRateInHz);

    public static native void write(byte[] audioData, int offset, int size);

    public static native void play();

    public static native void stop();

    public static native int getBufferPadding();

    public static native void shutdown();
}
