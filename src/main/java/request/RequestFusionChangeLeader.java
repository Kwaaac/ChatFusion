package main.java.request;

import main.java.OpCode;
import main.java.wrapper.InetIpv4ChatFusion;

import java.nio.ByteBuffer;

public record RequestFusionChangeLeader(InetIpv4ChatFusion address) implements Request {

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_CHANGE_LEADER;
    }

    @Override
    public int bufferLength() {
        return 1/*Byte*/ + address.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(address.encode());
    }
}