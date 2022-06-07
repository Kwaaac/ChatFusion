package main.java.request;

import main.java.OpCode;

import java.nio.ByteBuffer;

public record RequestMessagePublic() implements Request{

    @Override
    public int bufferLength() {
        return 0;
    }

    @Override
    public ByteBuffer encode() {
        return null;
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.MESSAGE;
    }
}
