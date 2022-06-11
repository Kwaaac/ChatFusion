package main.java.reader;

import main.java.BufferSerializable;
import main.java.wrapper.StringChatFusion;

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

    /**
     * Converts the message into a {@link String}
     * @return the message as a {@link String}
     */
    @Override
    public String toString() {
        return "[" + login + "]: " + msg;
    }

    /**
     * Gets the length of the {@link ByteBuffer} containing the message
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public int bufferLength() {
        return login().bufferLength() + msg().bufferLength();
    }

    /**
     * Fills the {@link ByteBuffer} with the necessary datas
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(login.encode()).put(msg.encode());
    }
}
