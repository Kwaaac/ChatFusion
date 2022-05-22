package main.java.Utils;

import main.java.BufferSerializable;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class InetSocketAddressConverter {

    /**
     *  Encode an InetSocketAddress into a {@link ByteBuffer} in read mode
     *
     * @param address InetSocketAddress to encode
     * @return {@link ByteBuffer} in read mode containing an InetSocketAddress
     */
    public static ByteBuffer encodeInetSocketAddress(InetSocketAddress address) {
        return new InetIpv4Converter(address).encode();
    }

    private record InetIpv4Converter(InetSocketAddress address) implements BufferSerializable {
        @Override
        public int bufferLength() {
            // 2 int -> size (4) and port + 4 bytes for the ipv4 address
            return Integer.BYTES * 2 + 4;
        }

        @Override
        public ByteBuffer encode() {
            var buffer = ByteBuffer.allocate(bufferLength());
            var addresses = address.getHostString().split("\\.");

            buffer.putInt(4);
            for (String s : addresses) {
                buffer.put((byte) Integer.parseInt(s));
            }
            buffer.putInt(address.getPort());

            return buffer.flip();
        }
    }
}
