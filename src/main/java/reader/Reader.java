package main.java.reader;

import java.nio.ByteBuffer;

public interface Reader<T> {
        ProcessStatus process(ByteBuffer bb);

        T get();

        void reset();

        enum ProcessStatus {DONE, REFILL, ERROR}
    }