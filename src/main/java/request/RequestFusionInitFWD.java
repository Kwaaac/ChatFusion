package main.java.request;

import main.java.OpCode;
import main.java.wrapper.InetIpv4ChatFusion;

import java.nio.ByteBuffer;

public record RequestFusionInitFWD(InetIpv4ChatFusion addressLeader) implements Request{
    @Override
    public int bufferLength() {
        return 1 + addressLeader.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(addressLeader.encode()).flip();
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_INIT_FWD;
    }
}
