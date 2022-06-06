package main.java.request;

import main.java.OpCode;

import java.nio.ByteBuffer;

public record RequestMessageFilePrivate() implements RequestMessage {
    @Override
    public OpCode getOpCode() {
        return null;
    }

    @Override
    public ByteBuffer getBuffer() {
        return null;
    }

    @Override
    public String getServer() {
        return null;
    }

    @Override
    public String getLogin() {
        return null;
    }
}
