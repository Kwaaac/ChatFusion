package main.java.request;

import main.java.OpCode;
import main.java.wrapper.InetIpv4ChatFusion;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public record RequestFusionInitOK(StringChatFusion serverName,
                                  InetIpv4ChatFusion address,
                                  int nbMembers,
                                  List<StringChatFusion> names) implements Request{
    public RequestFusionInitOK {
        if(serverName.size() > 100)
            throw new IllegalArgumentException("severName length superior than 100 UTF8 characters");
        if (nbMembers != names.size())
            throw new IllegalArgumentException("nbMembers and number of server given must be identical");
    }


    @Override
    public int bufferLength() {
        return 1
                + serverName.bufferLength()
                + address.bufferLength()
                + Integer.BYTES
                + names.stream().mapToInt(StringChatFusion::bufferLength).sum(); // names of every servers;
    }

    @Override
    public ByteBuffer encode() {
        var buffer = ByteBuffer.allocate(bufferLength());
        buffer.put(getOpCode().getOpCode()).put(serverName.encode()) // serverName
                .put(address.encode()) // address
                .putInt(nbMembers); // number of members

        names.stream()// Stream
                .map(StringChatFusion::encode) // From names to buffer
                .forEach(buffer::put); // add every bufferName to final buffer

        return buffer.flip();
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_INIT_OK;
    }
}
