package main.java.request;

import main.java.OpCode;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginAnonymous(StringChatFusion login) implements Request {
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
        return ByteBuffer.allocate(bufferLength()).put((byte) getOpCode().getOpCode()).put(login.encode());
    }
}
