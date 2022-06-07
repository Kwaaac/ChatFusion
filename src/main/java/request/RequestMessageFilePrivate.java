package main.java.request;

import main.java.OpCode;

import java.nio.ByteBuffer;

public record RequestMessageFilePrivate() implements Request {
    @Override
    public OpCode getOpCode() {
        return null;
    }

    @Override
    public int bufferLength() {
        return 0;
    }

    @Override
    public ByteBuffer encode() {
        return null;
    }
}
