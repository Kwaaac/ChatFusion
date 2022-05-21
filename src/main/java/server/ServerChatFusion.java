package main.java.server;

import main.java.OpCode;
import main.java.Utils.RequestFactory;
import main.java.reader.IntReader;
import main.java.reader.MessageReader;
import main.java.reader.Request;
import main.java.reader.StringReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ServerChatFusion {
    private static final Charset UTF8 = UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(ServerChatFusion.class.getName());
    private static final long TIMEOUT = 60_000;
    private final String serverName;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Thread console;
    private final StateController stateController = new StateController();
    private Context leader;
    private final HashMap<String, SelectionKey> clientConnected = new HashMap<>();
    private final HashMap<SelectionKey, String> serverConnected = new HashMap<>();

    public ServerChatFusion(String serverName, InetSocketAddress socketAddress) throws IOException {
        Objects.requireNonNull(serverName);
        if (serverName.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.serverName = serverName;
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(socketAddress);
        selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }

        new ServerChatFusion(args[0], new InetSocketAddress(Integer.parseInt(args[1]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumBetter serverName port");
    }

    private boolean isLeader() {
        return leader == null;
    }

    private void setLeader(Context context) {
        this.leader = context;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ServerChatFusion that && Objects.equals(serverSocketChannel, that.serverSocketChannel) && Objects.equals(selector, that.selector) && Objects.equals(console, that.console) && Objects.equals(stateController, that.stateController) && Objects.equals(leader, that.leader) && Objects.equals(clientConnected, that.clientConnected);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverSocketChannel, selector, console, stateController, leader, clientConnected);
    }

    private void consoleRun() {
        try (var scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                var msg = scanner.nextLine();
                switch (msg) {
                    case "INFO" -> {
                        System.out.println((selector.keys().size() - 1) + " Clients connected");
                    }
                    case "SHUTDOWN" -> {
                        stateController.updateState(State.STOP_ACCEPTING);
                    }
                    case "SHUTDOWNNOW" -> {
                        stateController.updateState(State.SHUTDOWN);
                        selector.wakeup();
                    }
                    default -> {
                    }
                }
            }
        }
        logger.info("Console thread stopping");
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        console.start();
        var lastCheck = System.currentTimeMillis();
        while (!Thread.interrupted() && stateController.getState() != State.SHUTDOWN) {
            try {
                if (System.currentTimeMillis() - lastCheck >= TIMEOUT) {
                    selector.keys().stream().filter(selectionKey -> !(selectionKey.channel() instanceof ServerSocketChannel)).map(selectionKey -> (Context) selectionKey.attachment()).forEach(Context::closeIfInactive);
                }
                selector.select(this::treatKey, TIMEOUT);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }

        selector.keys().forEach(this::silentlyClose);
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isAcceptable() && stateController.getState() == State.WORKING) {
                doAccept(key);
                Helpers.printSelectedKey(key); // for debug
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with main.java.client due to IOException", e);
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        var sc = ((ServerSocketChannel) key.channel()).accept();
        if (sc == null) {
            return;
        }

        sc.configureBlocking(false);
        var sk = sc.register(selector, SelectionKey.OP_READ);
        sk.attach(new Context(this, sk));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        if (sc instanceof ServerSocketChannel) {
            System.out.println("\tKey for ServerSocketChannel :");
        } else {
            System.out.println("\tKey for Client ");
        }
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a request to all connected clients queue
     *
     * @param request The request to broadcast to every client
     */
    private void broadcast(Request request, SelectionKey sender) {
        clientConnected.values().stream().map(key -> (Context) key.attachment()).forEach(context -> context.queueRequest(request));
        if(isLeader()) {
            serverConnected.keySet().stream().filter(s -> !s.equals(sender)).map(s -> (Context) s.attachment()).forEach(context -> context.queueRequest(request));
            return;
        }
        if(!sender.equals(leader.key))
            leader.queueRequest(request);
    }

    public void addClient(String login, SelectionKey key) {
        var duplicate = (clientConnected.putIfAbsent(login, key) != null);

        var client = (Context) key.attachment();

        if (duplicate) {
            // send connection refused
            client.queueRequest(RequestFactory.loginRefused());
            return;
        }

        // send connection accept
        client.queueRequest(RequestFactory.loginAccepted(serverName));
    }

    private enum State {
        WORKING, STOP_ACCEPTING, SHUTDOWN
    }

    private static class StateController {
        private final Object lock = new Object();
        private State state = State.WORKING;

        public void updateState(State state) {
            synchronized (lock) {
                this.state = state;
            }
        }

        public State getState() {
            synchronized (lock) {
                return state;
            }
        }
    }

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);

        private final IntReader intReader = new IntReader();
        private final StringReader stringReader = new StringReader();
        private final MessageReader messageReader = new MessageReader();
        private final ArrayDeque<Request> requestQueue = new ArrayDeque<>();
        private final ServerChatFusion server; // we could also have Context as an instance class
        private OpCode watcher = OpCode.IDLE;
        private boolean activeSinceLastTimeoutCheck = true;
        private boolean closed = false;

        private Context(ServerChatFusion server, SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() throws IOException {
            if (watcher == OpCode.IDLE) {
                var status = intReader.process(bufferIn, 15);

                switch (status) {
                    case DONE -> {
                        var optionalWatcher = OpCode.getOpCodeFromInt(intReader.get());
                        if (optionalWatcher.isPresent()) {
                            watcher = optionalWatcher.get();
                        } else {
                            // Close the connection if it sent a wrong OpCode
                            silentlyClose();
                        }
                    }
                    case ERROR -> {
                        silentlyClose();
                    }
                    case REFILL -> {
                        return;
                    }
                }


            }

            switch (watcher) {
                case LOGIN_ANONYMOUS, LOGIN_PASSWORD -> {
                    var status = stringReader.process(bufferIn, 30);
                    switch (status) {
                        case DONE -> {
                            var login = stringReader.get();
                            server.addClient(login, key);
                            stringReader.reset();
                        }
                        case ERROR -> {
                            queueRequest(RequestFactory.loginRefused());
                        }
                        case REFILL -> {
                        }
                    }
                }

                case MESSAGE -> {
                    var serverStatus = stringReader.process(bufferIn, 100);
                    switch (serverStatus) {
                        case DONE -> {
                            var loginStatus = messageReader.process(bufferIn, 30);
                            switch (loginStatus) {
                                case DONE -> {
                                    var message = messageReader.get();

                                    server.broadcast(RequestFactory.publicMessage(server.serverName, message), key);
                                }
                                 case ERROR -> {
                                    /*Message ignoré*/
                                 }
                                case REFILL -> {
                                }
                            }
                        }
                        case ERROR -> {
                            /*Message ignoré*/
                        }
                        case REFILL -> {
                        }
                    }
                }

                default -> {
                    // TODO temporary
                    throw new UnsupportedOperationException();
                }
            }

            /*
            for (; ; ) {
                Reader.ProcessStatus status = messageReader.process(bufferIn);
                switch (status) {
                    case DONE -> {
                        var value = messageReader.get();
                        server.broadcast(value);
                        messageReader.reset();
                    }
                    case REFILL -> {
                        return;
                    }
                    case ERROR -> {
                        silentlyClose();
                        return;
                    }
                }
            }
             */
        }

        /**
         * Add a request to the request queue, tries to fill bufferOut and updateInterestOps
         *
         * @param request request containing the opcode of the request and a buffer with the request's content
         */
        public void queueRequest(Request request) {
            requestQueue.add(request);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            while (!requestQueue.isEmpty()) {
                var request = requestQueue.peek();
                if (bufferOut.remaining() >= request.length()) {
                    request = requestQueue.pop();
                    bufferOut.putInt(request.code().getOpCode()).put(request.buffer());
                }
            }
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * updateInterestOps and after the call. Also it is assumed that process has
         * been be called just before updateInterestOps.
         */
        private void updateInterestOps() {
            int ops = 0;

            if (!closed && bufferIn.hasRemaining()) {
                ops |= SelectionKey.OP_READ;
            }

            if (!closed && (bufferOut.position() != 0 || !requestQueue.isEmpty())) {
                ops |= SelectionKey.OP_WRITE;
            }

            if (ops == 0) {
                silentlyClose();
                return;
            }

            key.interestOps(ops);
        }

        public void closeIfInactive() {
            if (!activeSinceLastTimeoutCheck) {
                silentlyClose();
            }

            // disabled for now
            activeSinceLastTimeoutCheck = true;
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doRead and after the call
         *
         * @throws IOException
         */
        private void doRead() throws IOException {
            activeSinceLastTimeoutCheck = true;
            closed = (sc.read(bufferIn) == -1);
            processIn();
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException
         */

        private void doWrite() throws IOException {
            activeSinceLastTimeoutCheck = true;
            sc.write(bufferOut.flip());
            bufferOut.compact();
            updateInterestOps();
        }

    }

    static class Helpers {
        /***
         * Theses methods are here to help understanding the behavior of the selector
         ***/

        private static String interestOpsToString(SelectionKey key) {
            if (!key.isValid()) {
                return "CANCELLED";
            }
            int interestOps = key.interestOps();
            var joiner = new StringJoiner("|");
            if ((interestOps & SelectionKey.OP_ACCEPT) != 0) joiner.add("OP_ACCEPT");
            if ((interestOps & SelectionKey.OP_READ) != 0) joiner.add("OP_READ");
            if ((interestOps & SelectionKey.OP_WRITE) != 0) joiner.add("OP_WRITE");
            return joiner.toString();
        }

        static void printKeys(Selector selector) {
            var selectionKeySet = selector.keys();
            if (selectionKeySet.isEmpty()) {
                System.out.println("The selector contains no key : this should not happen!");
                return;
            }
            System.out.println("The selector contains:");
            for (var key : selectionKeySet) {
                var channel = key.channel();
                if (channel instanceof ServerSocketChannel) {
                    System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
                } else {
                    var sc = (SocketChannel) channel;
                    System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
                }
            }
        }

        private static String remoteAddressToString(SocketChannel sc) {
            try {
                return sc.getRemoteAddress().toString();
            } catch (IOException e) {
                return "???";
            }
        }

        static void printSelectedKey(SelectionKey key) {
            var channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
            } else {
                var sc = (SocketChannel) channel;
                System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
            }
        }

        private static String possibleActionsToString(SelectionKey key) {
            if (!key.isValid()) {
                return "CANCELLED";
            }
            var joiner = new StringJoiner(" and ");
            if (key.isAcceptable()) joiner.add("ACCEPT");
            if (key.isReadable()) joiner.add("READ");
            if (key.isWritable()) joiner.add("WRITE");
            return joiner.toString();
        }
    }
}