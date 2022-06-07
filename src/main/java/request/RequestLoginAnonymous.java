package main.java.request;

import main.java.OpCode;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginAnonymous(StringChatFusion login) implements Request {
    public RequestLoginAnonymous {
        if (login.bufferLength() - Integer.BYTES > 30) {
            throw new IllegalArgumentException("severName length superior than 30 UTF8 characters");
        }
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.LOGIN_ANONYMOUS;
    }

    @Override
    public int bufferLength() {
        return 1/*Byte*/ + login.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(login.encode());
    }
}
