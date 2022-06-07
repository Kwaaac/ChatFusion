package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginPassword(StringChatFusion login, StringChatFusion password) implements Request {
    public RequestLoginPassword {
        if(login.size() > 30)
            throw new IllegalArgumentException("Login length superior than 30 UTF8 characters");
        if(password.size() > 30)
            throw new IllegalArgumentException("Password length superior than 30 UTF8 characters");
    }

    @Override
    public int bufferLength() {
        return 1 + login.bufferLength() + password.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(login.encode()).put(password.encode()).flip();
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.LOGIN_PASSWORD;
    }
}
