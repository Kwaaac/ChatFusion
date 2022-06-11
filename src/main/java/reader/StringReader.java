package main.java.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {
    private static final int BUFFER_SIZE = 1024;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final IntReader intReader = new IntReader();
    private ByteBuffer internalBuffer = ByteBuffer.allocate(Integer.BYTES);
    private State state = State.WAIT_INT;
    private String value;
    private int sizeValue = -1;

    /**
     * Retrieves the {@link String} from the {@link ByteBuffer} and stores them
     * @param buffer the {@link ByteBuffer} containing data
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    public ProcessStatus process(ByteBuffer buffer) {
        if (this.state == State.DONE || this.state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (state == State.WAIT_INT) {
            var status = intReader.process(buffer);
            switch (status) {
                case DONE -> {
                    sizeValue = intReader.get();
                    if (sizeValue < 0 || sizeValue > BUFFER_SIZE) {
                        this.state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    intReader.reset();
                    internalBuffer = ByteBuffer.allocate(sizeValue);
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

        buffer.flip();
        try {
            if (buffer.remaining() <= this.internalBuffer.remaining()) {
                this.internalBuffer.put(buffer);
            } else {
                int oldLimit = buffer.limit();
                var remaining = this.internalBuffer.remaining();
                var delta = buffer.position() == 0 ? 0 : sizeValue - buffer.position() + 1;
                buffer.limit(remaining + delta);
                this.internalBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
        } finally {
            buffer.compact();
        }

        if (this.internalBuffer.hasRemaining()) {
            return ProcessStatus.REFILL;
        } else {
            this.internalBuffer.flip();
            this.state = State.DONE;
            this.value = UTF8.decode(internalBuffer).toString();
            internalBuffer = ByteBuffer.allocate(Integer.BYTES);
            return ProcessStatus.DONE;
        }

    }

    /**
     * Gets the {@link String} retrieved by the process method
     * @return the {@link String} associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
     */
    public String get() {
        if (this.state != State.DONE) {
            throw new IllegalStateException();
        } else {
            return this.value;
        }
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    public void reset() {
        this.state = State.WAIT_INT;
        this.internalBuffer = ByteBuffer.allocate(Integer.BYTES);
        intReader.reset();

    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_INT, WAIT_STRING, ERROR
    }
}