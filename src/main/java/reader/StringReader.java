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

    public String get() {
        if (this.state != State.DONE) {
            throw new IllegalStateException();
        } else {
            return this.value;
        }
    }

    public void reset() {
        this.state = State.WAIT_INT;
        this.internalBuffer = ByteBuffer.allocate(Integer.BYTES);
        intReader.reset();

    }

    private enum State {
        DONE, WAIT_INT, WAIT_STRING, ERROR
    }
}