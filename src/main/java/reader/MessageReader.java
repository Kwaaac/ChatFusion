package main.java.reader;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {
    private final StringReader stringReader = new StringReader();
    private String login;
    private String msg;
    private State state = State.WAIT_LOGIN;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (this.state == State.DONE || this.state == State.ERROR) {
            throw new IllegalStateException();
        }

        for (; ; ) {
            var status = stringReader.process(bb);
            switch (status) {
                case DONE -> {
                    if (state == State.WAIT_LOGIN) {
                        login = stringReader.get();
                        stringReader.reset();
                        state = State.WAIT_MSG;
                        break;
                    }

                    msg = stringReader.get();
                    this.state = State.DONE;
                    return ProcessStatus.DONE;
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
    }

    @Override
    public Message get() {
        if (this.state != State.DONE) {
            throw new IllegalStateException();
        } else {
            return new Message(login, msg);
        }
    }

    @Override
    public void reset() {
        this.state = State.WAIT_LOGIN;
        stringReader.reset();
    }

    private enum State {
        DONE, WAIT_LOGIN, WAIT_MSG, ERROR
    }
}