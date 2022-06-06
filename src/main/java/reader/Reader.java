package main.java.reader;

import main.java.BufferSerializable;

import java.nio.ByteBuffer;

public interface Reader<T> {
        ProcessStatus process(ByteBuffer bb, int maxValue);

        T get();

        void reset();

        enum ProcessStatus {DONE, REFILL, ERROR}
    }