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
        return new InetIpv4ChatFusion(address).encode();
    }
}
