package main.java.reader.fusion;

import main.java.Utils.RequestFactory;
import main.java.reader.InetSocketAddressReader;
import main.java.reader.IntReader;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class RequestFusionInitReader implements Reader<Request> {
    Reader<String> stringReader = new StringReader();
    Reader<InetSocketAddress> addressReader = new InetSocketAddressReader();
    Reader<Integer> intReader = new IntReader();
    String serverName;
    InetSocketAddress address;
    int nbMembers;

    int index_members;


    String[] serverNames;
    private State state = State.WAIT_NAME;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        for (; ; ) {
            switch (state) {
                case DONE, ERROR -> throw new IllegalStateException();

                case WAIT_NAME -> {
                    var serverStatus = stringReader.process(bb);
                    if (serverStatus == ProcessStatus.DONE) {
                        serverName = stringReader.get();
                        stringReader.reset();
                        state = State.WAIT_ADDRESS;
                    } else {
                        return serverStatus;
                    }
                }

                case WAIT_ADDRESS -> {
                    var addressStatus = addressReader.process(bb);
                    if (addressStatus == ProcessStatus.DONE) {
                        address = addressReader.get();
                        addressReader.reset();
                        state = State.WAIT_NB_MEMBERS;
                    } else {
                        return addressStatus;
                    }
                }
                case WAIT_NB_MEMBERS -> {
                    var nbMembersStatus = intReader.process(bb);
                    if (nbMembersStatus == ProcessStatus.DONE) {
                        nbMembers = intReader.get();
                        intReader.reset();

                        index_members = 0;
                        serverNames = new String[nbMembers];
                        state = State.WAIT_SERVER_NAMES;
                    } else {
                        return nbMembersStatus;
                    }
                }

                case WAIT_SERVER_NAMES -> {
                    var serverStatus = stringReader.process(bb);
                    if (serverStatus == ProcessStatus.DONE) {
                        serverName = stringReader.get();
                        stringReader.reset();

                        serverNames[index_members++] = serverName;
                        if (index_members == nbMembers) {
                            state = State.DONE;
                            return ProcessStatus.DONE;
                        }
                    } else {
                        return serverStatus;
                    }
                }
            }
        }
    }

    @Override
    public Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        reset();
        return RequestFactory.fusionInit(serverName, address, nbMembers, serverNames);
    }

    @Override
    public void reset() {
        stringReader.reset();
        addressReader.reset();
        intReader.reset();
        state = State.WAIT_NAME;
    }

    private enum State {
        DONE, WAIT_NAME, WAIT_ADDRESS, WAIT_NB_MEMBERS, WAIT_SERVER_NAMES, ERROR
    }
}
