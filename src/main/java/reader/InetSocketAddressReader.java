package main.java.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

import static main.java.reader.Reader.ProcessStatus.DONE;

public class InetSocketAddressReader implements Reader<InetSocketAddress> {
    private static final int BUFFER_SIZE = 16;
    private final IntReader intReader = new IntReader();
    private final ByteBuffer integerBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
    private final ByteBuffer addressBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private State state = State.WAIT_SIZE;
    private int size;
    private String hostname;
    private int port;

    @Override
    public ProcessStatus process(ByteBuffer bb, int maxValue) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (state == State.WAIT_SIZE) {
            var status = intReader.process(bb, maxValue);

            switch (status) {
                case DONE -> {
                    size = intReader.get();
                    if (size != 4 && size != 16 && size > maxValue) {
                        this.state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    intReader.reset();
                    integerBuffer.clear();
                    state = State.WAIT_IP;
                    addressBuffer.limit(size);
                }
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    this.state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
            }
        }

        if (state == State.WAIT_IP) {
            bb.flip();
            try {
                if (bb.remaining() <= this.addressBuffer.remaining()) {
                    this.addressBuffer.put(bb);
                } else {
                    int oldLimit = bb.limit();
                    var remaining = this.addressBuffer.remaining();
                    var delta = bb.position() == 0 ? 0 : size - bb.position() + 1;
                    bb.limit(remaining + delta);
                    this.addressBuffer.put(bb);
                    bb.limit(oldLimit);
                }
            } finally {
                bb.compact();
            }

            if (this.addressBuffer.hasRemaining()) {
                return ProcessStatus.REFILL;
            } else {
                this.addressBuffer.flip();
                this.state = State.WAIT_PORT;

                // Parsing of the IP adress
                StringJoiner sj;
                if (size == 4) {
                    sj = new StringJoiner(".");
                    for (int i = 0; i < size; i++) {
                        sj.add((addressBuffer.get() & 0xFF) + "");
                    }
                } else {
                    sj = new StringJoiner(":");
                    for (int i = 0; i < size; i++) {
                        sj.add((addressBuffer.get() & 0xFF) + (addressBuffer.get() & 0xFF) + "");
                    }
                }
                hostname = sj.toString();
            }
        }

        var status = intReader.process(bb, maxValue);
        switch (status) {
            case DONE -> {
                port = intReader.get();
                if (port < 0 || port > 65_535) {
                    this.state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                intReader.reset();
                integerBuffer.clear();
                state = State.DONE;
                return DONE;
            }
            case REFILL -> {
                return ProcessStatus.REFILL;
            }
            case ERROR -> {
                this.state = State.ERROR;
                return ProcessStatus.ERROR;
            }
        }

        throw new AssertionError();
    }

    @Override
    public InetSocketAddress get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new InetSocketAddress(hostname, port);
    }

    @Override
    public void reset() {
        intReader.reset();
        state = State.WAIT_SIZE;
        integerBuffer.clear();
        addressBuffer.clear();
    }

    private enum State {
        DONE, WAIT_SIZE, WAIT_IP, WAIT_PORT, ERROR
    }
}
