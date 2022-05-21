package test.main.java.reader;

import main.java.reader.InetSocketAdressReader;
import main.java.reader.Reader;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

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
        var sr = new InetSocketAdressReader();
        assertEquals(Reader.ProcessStatus.DONE, sr.process(bb, 16));
        assertEquals(expected, sr.get());
    }
}