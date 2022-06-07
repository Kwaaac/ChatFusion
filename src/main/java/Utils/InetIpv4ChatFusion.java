package main.java.Utils;

import main.java.BufferSerializable;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public record InetIpv4ChatFusion(InetSocketAddress address) implements BufferSerializable {
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