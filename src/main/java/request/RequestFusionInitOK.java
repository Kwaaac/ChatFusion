package main.java.request;

import main.java.OpCode;
import main.java.wrapper.InetIpv4ChatFusion;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;
import java.util.Arrays;

public record RequestFusionInitOK(StringChatFusion serverName,
                                  InetIpv4ChatFusion address,
                                  int nbMembers,
                                  StringChatFusion... names) implements Request{
    public RequestFusionInitOK {
        if(serverName.size() > 100)
            throw new IllegalArgumentException("severName length superior than 100 UTF8 characters");
        if (nbMembers != names.length)
            throw new IllegalArgumentException("nbMembers and number of server given must be identical");
    }


    @Override
    public int bufferLength() {
        return 1
                + serverName.bufferLength()
                + address.bufferLength()
                + Integer.BYTES
                + Arrays.stream(names).mapToInt(StringChatFusion::bufferLength).sum(); // names of every servers;
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
        return OpCode.FUSION_INIT_OK;
    }
}
