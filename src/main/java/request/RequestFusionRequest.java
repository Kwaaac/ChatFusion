package main.java.request;

import main.java.OpCode;
import main.java.Utils.InetIpv4ChatFusion;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestFusionRequest(InetIpv4ChatFusion address) implements Request {

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_REQUEST;
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
