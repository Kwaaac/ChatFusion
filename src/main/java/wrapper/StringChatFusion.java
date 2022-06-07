package main.java.wrapper;

import main.java.BufferSerializable;

import java.nio.ByteBuffer;

/**
 * This record represent a way of representing a String easily convertible to a {@link ByteBuffer}.
 * Allowing to get the total length of a {@link ByteBuffer} conversion and to convert it into a {@link ByteBuffer} in UTF8
 */
public record StringChatFusion(String string) implements BufferSerializable {
    public int size() {
        return string.getBytes(UTF8).length;
    }

    @Override
    public int bufferLength() {
        return Integer.BYTES + size();
    }

    @Override
    public ByteBuffer encode() {
        var buffer = ByteBuffer.allocate(bufferLength());
        return buffer.putInt(size()).put(UTF8.encode(string)).flip();
    }
}
