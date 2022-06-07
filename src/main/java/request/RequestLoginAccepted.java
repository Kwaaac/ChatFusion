package main.java.request;

import main.java.OpCode;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginAccepted(StringChatFusion serverName) implements Request {
    public RequestLoginAccepted {
        if (serverName.size() > 100) {
            throw new IllegalArgumentException("severName length superior than 100 UTF8 characters");
        }
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.LOGIN_ACCEPTED;
    }

    @Override
    public int bufferLength() {
        return 1/*Byte*/ + serverName.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(serverName.encode());
    }
}
