package main.java.reader;

import main.java.BufferSerializable;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;

public record Message(StringChatFusion login, StringChatFusion msg) implements BufferSerializable {
    public Message {
        if (login.size() > 30) {
            throw new IllegalArgumentException("login length superior than 30 UTF8 characters");
        }
        if (msg.size() > 1024) {
            throw new IllegalArgumentException("message length superior than 1024 UTF8 characters");
        }
    }

    @Override
    public String toString() {
        return "[" + login + "]: " + msg;
    }

    @Override
    public int bufferLength() {
        return login().bufferLength() + msg().bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(login.encode()).put(msg.encode());
    }
}
