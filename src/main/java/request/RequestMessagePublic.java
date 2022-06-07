package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestMessagePublic(StringChatFusion serverName, StringChatFusion login,
                                   StringChatFusion message) implements Request {
    public RequestMessagePublic {
        if (serverName.size() > 100) {
            throw new IllegalArgumentException("severName length superior than 100 UTF8 characters");
        }
        if (login.size() > 30) {
            throw new IllegalArgumentException("login length superior than 30 UTF8 characters");
        }
        if (message.size() > 1024) {
            throw new IllegalArgumentException("message length superior than 1024 UTF8 characters");
        }
    }

    @Override
    public int bufferLength() {
        return 1 + Integer.BYTES * 3 + serverName.bufferLength() + message.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()) // OpCode
                .put(serverName.encode()) // Server Name size + Server name
                .put(login.encode()) // Login size + login
                .put(message.encode()); // message size + message

    }

    @Override
    public OpCode getOpCode() {
        return OpCode.MESSAGE;
    }
}
