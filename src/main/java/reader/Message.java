package main.java.reader;

import main.java.BufferSerializable;

import java.nio.ByteBuffer;

public record Message(String login, String msg) implements BufferSerializable {
    @Override
    public String toString() {
        return "[" + login + "]: " + msg;
    }

    @Override
    public int bufferLength() {
        return login.getBytes(UTF8).length + msg.getBytes(UTF8).length + Integer.BYTES * 2;
    }

    @Override
    public ByteBuffer encode() {
        var buffer = ByteBuffer.allocate(bufferLength());
        return buffer.putInt(login.getBytes(UTF8).length).put(UTF8.encode(login)).putInt(msg.getBytes(UTF8).length).put(UTF8.encode(msg)).flip();
    }
}
