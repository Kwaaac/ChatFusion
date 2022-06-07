package main.java.reader.login;

import main.java.Utils.StringChatFusion;
import main.java.reader.Reader;
import main.java.reader.StringChatFusionReader;
import main.java.request.Request;
import main.java.request.RequestLoginAnonymous;

import java.nio.ByteBuffer;

public class RequestLoginAnonymousReader implements Reader<Request> {
    StringChatFusion value;
    Reader<StringChatFusion> stringReader = new StringChatFusionReader();
    private State state = State.WAIT_LOGIN;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        var status = stringReader.process(bb);
        return switch (status) {
            case DONE -> {
                value = stringReader.get();
                state = State.DONE;
                yield ProcessStatus.DONE;
            }
            case ERROR -> {
                state = State.ERROR;
                yield ProcessStatus.ERROR;
            }
            case REFILL -> ProcessStatus.REFILL;
        };
    }

    @Override
    public Request get() {
        if (state != State.DONE || value == null) {
            throw new IllegalStateException();
        }
        reset();
        return new RequestLoginAnonymous(value);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAIT_LOGIN;
    }

    private enum State {
        DONE, WAIT_LOGIN, ERROR
    }
}
