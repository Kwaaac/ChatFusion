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

    /**
     * Retrieves the {@link InetSocketAddress} from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing data
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (state == State.WAIT_SIZE) {
            var status = intReader.process(bb);

            switch (status) {
                case DONE -> {
                    size = intReader.get();
                    if (size != 4 && size != 16) {
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

        var status = intReader.process(bb);
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


    /**
     * Gets the {@link InetSocketAddress} retrieved by the process method
     * @return the parametrized type associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
     */
    @Override
    public InetSocketAddress get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new InetSocketAddress(hostname, port);
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        intReader.reset();
        state = State.WAIT_SIZE;
        integerBuffer.clear();
        addressBuffer.clear();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_SIZE, WAIT_IP, WAIT_PORT, ERROR
    }
}
