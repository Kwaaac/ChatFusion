package main.java.reader;

import java.nio.ByteBuffer;

public class IntReader implements Reader<Integer> {

    private final ByteBuffer internalBuffer = ByteBuffer.allocate(Integer.BYTES); // write-mode
    private State state = State.WAITING;
    private int value;

    /**
     * Retrieves the {@link Integer} from the {@link ByteBuffer} and stores them
     * @param buffer the bytebuffer containing data
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        buffer.flip();
        try {
            if (buffer.remaining() <= internalBuffer.remaining()) {
                internalBuffer.put(buffer);
            } else {
                var oldLimit = buffer.limit();
                buffer.limit(internalBuffer.remaining());
                internalBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
        } finally {
            buffer.compact();
        }
        if (internalBuffer.hasRemaining()) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalBuffer.flip();
        value = internalBuffer.getInt();
        return ProcessStatus.DONE;
    }

    /**
     * Gets the {@link Integer} retrieved by the process method
     * @return the {@link Integer} associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
     */
    @Override
    public Integer get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        state = State.WAITING;
        internalBuffer.clear();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    public enum State {
        DONE, WAITING, ERROR
    }

}