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
        assertEquals(Reader.ProcessStatus.DONE, sr.process(bb, 16));
        assertEquals(expected, sr.get());
    }

    private void aled(String... args) {
        System.out.println(Arrays.toString(args));
    }

    @Test
    public void test() {
        var lst = List.of("Bob", "Guillaume", "Max");

        var names = lst.toArray(new String[0]);
        aled(names);
    }
}