package reader;

import java.nio.ByteBuffer;

public class SocketAdressReader implements Reader<SocketAdressPort>{
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        return null;
    }

    @Override
    public SocketAdressPort get() {
        return null;
    }

    @Override
    public void reset() {

    }
}
