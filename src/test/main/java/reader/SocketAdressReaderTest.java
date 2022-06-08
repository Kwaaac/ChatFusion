package test.main.java.reader;

import main.java.reader.InetSocketAddressReader;
import main.java.reader.Reader;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SocketAdressReaderTest {

    @Test
    public void ipv4() {
        var expected = new InetSocketAddress("255.255.255.255", 8080);
        var bb = ByteBuffer.allocate(1024);
        bb.putInt(4);
        bb.put((byte) 255);
        bb.put((byte) 255);
        bb.put((byte) 255);
        bb.put((byte) 255);
        bb.putInt(8080);
        var sr = new InetSocketAddressReader();
        assertEquals(Reader.ProcessStatus.DONE, sr.process(bb));
        assertEquals(expected, sr.get());
    }
}