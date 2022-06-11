package main.java.request;

import main.java.OpCode;
import main.java.wrapper.InetIpv4ChatFusion;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;
import java.util.List;

public record RequestFusionInit(StringChatFusion serverName, InetIpv4ChatFusion address, int nbMembers,
                                List<StringChatFusion> names) implements Request {

    public RequestFusionInit {
        if (serverName.size() > 100) {
            throw new IllegalArgumentException("severName length superior than 100 UTF8 characters");
        }

        if (nbMembers != names.size()) {
            throw new IllegalArgumentException("nbMembers and number of server given must be identical");
        }
    }

    /**
     * Returns the length of a {@link ByteBuffer} containing the {@link Request} data
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public int bufferLength() {
        return 1 // OpCode
                + serverName.bufferLength() // serverName
                + address().bufferLength() // address
                + Integer.BYTES // nbMembers
                + names.stream().mapToInt(StringChatFusion::bufferLength).sum(); // names of every server
    }

    /**
     * Encodes the necessary data and puts it in the {@link ByteBuffer}
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        var buffer = ByteBuffer.allocate(bufferLength());
        buffer.put(getOpCode().getOpCode()) // OpCode
                .put(serverName.encode()) // serverName
                .put(address.encode()) // address
                .putInt(nbMembers); // number of members

        names.stream()// Stream
                .map(StringChatFusion::encode) // From names to buffer
                .forEach(buffer::put); // add every bufferName to final buffer

        return buffer.flip();
    }

    /**
     * Gets the {@link OpCode} associated with the {@link Request}
     * @return the {@link OpCode} associated with the {@link Request}
     */
    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_INIT;
    }
}
