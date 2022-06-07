package main.java.request;

import main.java.OpCode;

import java.nio.ByteBuffer;

public record RequestFusionInitKO() implements Request {
    @Override
    public int bufferLength() {
        return 1; // OpCode
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()); // OpCode
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_INIT_KO;
    }
}
