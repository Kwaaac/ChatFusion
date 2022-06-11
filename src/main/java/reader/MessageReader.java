package main.java.reader;

import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {
    private final StringReader stringReader = new StringReader();
    private String login;
    private String msg;
    private State state = State.WAIT_LOGIN;

    /**
     * Retrieves the {@link Message} from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing datas
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
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

    /**
     * Gets the {@link Message} retrieved by the process method
     * @return the {@link Message} associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
     */
    @Override
    public Message get() {
        if (this.state != State.DONE) {
            throw new IllegalStateException();
        } else {
            return new Message(new StringChatFusion(login), new StringChatFusion((msg)));
        }
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        this.state = State.WAIT_LOGIN;
        stringReader.reset();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_LOGIN, WAIT_MSG, ERROR
    }
}