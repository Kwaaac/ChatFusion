package main.java;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Represent an object serializable in a {@link ByteBuffer}
 */
public interface BufferSerializable {

    public static final Charset UTF8 = StandardCharsets.UTF_8;

    /**
     * Number of bytes in UTF-8 encoding it would take in a {@link ByteBuffer}
     *
     * @return the length of the object
     */
    int bufferLength();

    /**
     * Encode the object in a {@link ByteBuffer} in UTF-8 encoding
     * <p>
     * The buffer should be in read-mode
     *
     * @return a {@link ByteBuffer} containing the object in UTF-8 encoding
     */
    ByteBuffer encode();
}
