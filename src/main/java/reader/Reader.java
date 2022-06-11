package main.java.reader;

import java.nio.ByteBuffer;

public interface Reader<T> {

    /**
     * Retrieves data from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing data
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
        ProcessStatus process(ByteBuffer bb);

    /**
     * Gets the parametrized type retrieved by the process method
     * @return the parametrized type associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
     */
        T get();

    /**
     * Resets the {@link Reader} to make it reusable
     */
        void reset();

    /**
     * The different possible states for the buffer data recovery
     */
        enum ProcessStatus {DONE, REFILL, ERROR}
    }