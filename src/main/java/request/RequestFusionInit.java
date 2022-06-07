package main.java.request;

import main.java.OpCode;
import main.java.Utils.InetIpv4ChatFusion;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;
import java.util.Arrays;

public record RequestFusionInit(StringChatFusion serverName, InetIpv4ChatFusion address, int nbMembers,
                                StringChatFusion... names) implements Request {
    @Override
    public int bufferLength() {
        return 1 // OpCode
                + serverName.bufferLength() // serverName
                + address().bufferLength() // address
                + Integer.BYTES // nbMembers
                + Arrays.stream(names).mapToInt(StringChatFusion::bufferLength).sum(); // names of every servers
    }
    @Override
    public ByteBuffer encode() {
        var buffer = ByteBuffer.allocate(bufferLength());
        buffer.put(getOpCode().getOpCode()).put(serverName.encode()) // serverName
                .put(address.encode()) // address
                .putInt(nbMembers); // number of members

        Arrays.stream(names)// Stream
                .map(StringChatFusion::encode) // From names to buffer
                .forEach(buffer::put); // add every bufferName to final buffer

        return buffer;
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_INIT;
    }
}
