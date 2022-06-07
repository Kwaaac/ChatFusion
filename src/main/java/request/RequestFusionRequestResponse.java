package main.java.request;

import main.java.OpCode;

import java.nio.ByteBuffer;

public record RequestFusionRequestResponse(Byte status) implements Request{
    @Override
    public int bufferLength() {
        return 1 + Byte.BYTES;
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(status);
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_REQUEST_RESPONSE;
    }
}
