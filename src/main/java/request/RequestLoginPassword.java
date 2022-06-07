package main.java.request;

import main.java.OpCode;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginPassword(StringChatFusion login, StringChatFusion password) implements Request {
    @Override
    public int bufferLength() {
        return 1 + login.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put((byte) getOpCode().getOpCode()).put(login.encode()).put(password.encode());
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.LOGIN_PASSWORD;
    }
}
