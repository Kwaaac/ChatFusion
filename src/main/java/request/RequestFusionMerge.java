package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestFusionMerge(StringChatFusion serverName) implements Request{

    @Override
    public int bufferLength() {
        return 1 + serverName.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(serverName.encode());
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_MERGE;
    }
}
