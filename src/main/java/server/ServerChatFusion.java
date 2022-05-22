package main.java.server;

import main.java.OpCode;
import main.java.Utils.RequestFactory;
import main.java.reader.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
    private final SocketChannel leaderSocketChannel = SocketChannel.open();
    private final Selector selector;
    private final Thread console;
    private final StateFusionController stateController = new StateFusionController();
    private final HashMap<String, SelectionKey> clientConnected = new HashMap<>();
    private final ConcurrentHashMap<SelectionKey, String> serverConnected = new ConcurrentHashMap<>();
    private final List<String> memberAddList = new ArrayList<>();
    private FusionState fusionState = FusionState.IDLE;
    private Context leader;

    public ServerChatFusion(String serverName, InetSocketAddress socketAddress) throws IOException {
        Objects.requireNonNull(serverName);
        if (serverName.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.serverName = serverName;
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(socketAddress);
        leaderSocketChannel.configureBlocking(false);
        selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }

        var server = new ServerChatFusion(args[0], new InetSocketAddress("127.0.0.1", Integer.parseInt(args[1])));

        var keyself = server.leaderSocketChannel.register(server.selector, SelectionKey.OP_CONNECT);
        server.leader = new Context(server, keyself);
        keyself.attach(server.leader);
        System.out.println(server.serverSocketChannel.getLocalAddress());
        server.launch();
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
                        System.out.println((selector.keys().size() - 1) + " Server and Client connected");
                    }
                    case "SHUTDOWN" -> {
                        stateController.updateState(State.STOP_ACCEPTING);
                    }
                    case "SHUTDOWNNOW" -> {
                        stateController.updateState(State.SHUTDOWN);
                        selector.wakeup();
                    }
                    case String msgString && msgString.startsWith("FUSION") -> {
                        stateController.sendCommand(msgString.substring(7), selector);
                    }
                    default -> {
                    }
                }
            }
        } catch (InterruptedException e) {
            logger.info("Console thread Interrupted");
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
                processCommand();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }

        selector.keys().forEach(this::silentlyClose);
    }

    /**
     * Processes the command from the BlockingQueue
     */
    private void processCommand() throws IOException {
        var msg = stateController.processCommand();
        if (msg == null) {
            return;
        }

        var commands = msg.split(" ");
        System.out.println(Arrays.toString(commands));
        if (leaderSocketChannel.isConnected()) {
            leaderSocketChannel.close();
        }
        var remote = new InetSocketAddress(commands[0], Integer.parseInt(commands[1]));
        leaderSocketChannel.connect(remote);
        var key = leaderSocketChannel.register(selector, SelectionKey.OP_CONNECT);

        leader = new Context(this, key);
        key.attach(leader);
        System.out.println(leaderSocketChannel.isConnected());

        System.out.println("test: " + remote);
        String[] names = serverConnected.values().stream().toList().toArray(new String[0]);
        leader.queueRequest(RequestFactory.fusionInit(serverName, (InetSocketAddress) serverSocketChannel.getLocalAddress(), serverConnected.size(), names));
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
            if (key.isValid() && key.isConnectable()) {
                ((Context) key.attachment()).doConnect();
            }

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

        System.out.println(sk);
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        if (sc instanceof ServerSocketChannel) {
            System.out.println("\tKey for ServerSocketChannel :");
        } else {
            System.out.println("\tKey for Client ");
        }
        serverConnected.remove(key);
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

        if (isLeader()) {
            serverConnected.keySet().stream().filter(s -> !s.equals(sender)).map(s -> (Context) s.attachment()).forEach(context -> context.queueRequest(request));
            return;
        }

        if (!sender.equals(leader.key)) leader.queueRequest(request);
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

    private enum FusionState {
        PENDING_FUSION, IDLE
    }

    private static class StateFusionController {
        private final Object lock = new Object();
        private final BlockingQueue<String> requestFusionQueue = new ArrayBlockingQueue<>(10);
        private State state = State.WORKING;

        /**
         * Send instructions to the selector via a BlockingQueue and wake it up
         */
        public void sendCommand(String request, Selector selector) throws InterruptedException {
            synchronized (lock) {
                requestFusionQueue.add(request);
                selector.wakeup();
            }
        }

        public String processCommand() {
            synchronized (lock) {
                return requestFusionQueue.poll();
            }
        }

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

        private final InetSocketAddressReader addressReader = new InetSocketAddressReader();
        private final ArrayDeque<Request> requestQueue = new ArrayDeque<>();
        private final ServerChatFusion server; // we could also have Context as an instance class
        private OpCode watcher = OpCode.IDLE;
        private RequestState requestState = RequestState.ANYTHING;
        private boolean activeSinceLastTimeoutCheck = true;
        private boolean closed = false;

        private int index_members;

        private int nbMembers;
        private String serverSrc;

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
            System.out.println("OUIBONJOUR?");
            if (watcher == OpCode.IDLE) {
                var status = intReader.process(bufferIn, 15);

                switch (status) {
                    case DONE -> {
                        var optionalWatcher = OpCode.getOpCodeFromInt(intReader.get());
                        if (optionalWatcher.isPresent()) {
                            watcher = optionalWatcher.get();
                            intReader.reset();
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
                            stringReader.reset();

                            server.addClient(login, key);
                            watcher = OpCode.IDLE;
                        }
                        case ERROR -> {
                            queueRequest(RequestFactory.loginRefused());
                            watcher = OpCode.IDLE;
                        }
                        case REFILL -> {
                        }
                    }
                }
                case MESSAGE -> {
                    messageServerHandle();
                }

                case FUSION_INIT_KO -> {
                    server.fusionState = FusionState.IDLE;
                    logger.info("Fusion denied !");
                }

                case FUSION_INIT -> {
                    System.out.println("COUUUCOUUUUUUU");

                    if (!server.isLeader()) {
                        ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitForward((InetSocketAddress) server.leader.sc.getRemoteAddress()));
                        requestState = RequestState.ANYTHING;
                        watcher = OpCode.IDLE;
                        return;
                    }

                    if (requestState == RequestState.SERVER_NAME_SRC) {
                        var serverStatus = stringReader.process(bufferIn, 100);
                        switch (serverStatus) {
                            case DONE -> {
                                serverSrc = stringReader.get();
                                stringReader.reset();
                                if (server.serverConnected.containsValue(serverSrc)) {
                                    ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitKO());
                                    requestState = RequestState.ANYTHING;
                                    watcher = OpCode.IDLE;
                                    return;
                                }


                                var addressStatus = addressReader.process(bufferIn, 16);
                                switch (addressStatus) {
                                    case DONE -> {
                                        var address = addressReader.get();
                                        addressReader.reset();
                                        var nbMembersStatus = intReader.process(bufferIn, 42);
                                        switch (nbMembersStatus) {
                                            case DONE -> {
                                                nbMembers = intReader.get();
                                                intReader.reset();

                                                for (; index_members < nbMembers; index_members++) {
                                                    var memberStatus = stringReader.process(bufferIn, 30);
                                                    switch (memberStatus) {
                                                        case DONE -> {
                                                            var member = stringReader.get();
                                                            if (server.clientConnected.containsKey(member)) {
                                                                ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitKO());
                                                                return;
                                                            }
                                                            stringReader.reset();
                                                            server.memberAddList.add(member);
                                                        }
                                                        case ERROR -> {
                                                            watcher = OpCode.IDLE;
                                                            requestState = RequestState.ANYTHING;
                                                            return;
                                                        }

                                                        case REFILL -> {
                                                            return;
                                                        }
                                                    }
                                                }
//                                                ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitOK(server.serverName, server.serverSocketChannel, server.clientConnected.size(), server.clientConnected.values().toArray()));
                                            }
                                            case ERROR -> {
                                                addressReader.reset();
                                            }
                                            case REFILL -> {
                                            }
                                        }
                                    }
                                    case ERROR -> {
                                        addressReader.reset();
                                    }
                                    case REFILL -> {
                                    }
                                }
                            }
                            case ERROR -> {
                                stringReader.reset();
                            }
                            case REFILL -> {
                            }
                        }
                    }
                }
                default -> {
                    // TODO temporary
                    throw new UnsupportedOperationException();
                }
            }
        }

        private void messageServerHandle() {
            if (requestState == RequestState.ANYTHING) {
                requestState = RequestState.SERVER_NAME_SRC;
            }

            /* Fetch server name*/
            if (requestState == RequestState.SERVER_NAME_SRC) {
                var serverStatus = stringReader.process(bufferIn, 100);
                switch (serverStatus) {
                    case DONE -> {
                        serverSrc = stringReader.get();
                        stringReader.reset();
                        requestState = RequestState.LOGIN_SRC;
                    }
                    case ERROR -> {
                        /*Message ignored*/
                        stringReader.reset();
                        watcher = OpCode.IDLE;
                        return;
                    }
                    case REFILL -> {
                        return;

                    }
                }
            }
            /* Fetch login & message*/
            if (requestState == RequestState.LOGIN_SRC) {
                var loginStatus = messageReader.process(bufferIn, 30);
                switch (loginStatus) {
                    case DONE -> {
                        var message = messageReader.get();
                        messageReader.reset();
                        var request = RequestFactory.publicMessage(serverSrc, message);
                        server.broadcast(request, key);
                        watcher = OpCode.IDLE;
                        requestState = RequestState.ANYTHING;
                    }
                    case ERROR -> {
                        /*Message ignoré*/
                        messageReader.reset();
                        watcher = OpCode.IDLE;
                        return;
                    }
                    case REFILL -> {
                        return;
                    }
                }
            }
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
                if (bufferOut.remaining() >= request.bufferLength()) {
                    request = requestQueue.pop();
                    // If there is a fusion request while the server is pending a fusion,
                    // then it's dismissed waiting for the current fusion to finish by putting
                    // the request to the last request of the queue
                    if (request.code() == OpCode.FUSION_INIT && server.fusionState == FusionState.PENDING_FUSION) {
                        requestQueue.add(request);
                        return;
                    }
                    try {
                        System.out.println("processOut " + this.sc.getRemoteAddress());
                    } catch (IOException e) {
                        System.out.println("pouet");
                    }
                    bufferOut.putInt(request.code().getOpCode()).put(request.buffer().clear());
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
            System.out.println(key);
            if (key.isConnectable()) {
                ops |= SelectionKey.OP_CONNECT;
            }

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
            System.out.println(key);
            System.out.println(ops);

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
            System.out.println(bufferOut);
            sc.write(bufferOut.flip());
            System.out.println(bufferOut);
            bufferOut.compact();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) return; // the selector gave a bad hint
            key.interestOps(SelectionKey.OP_WRITE);
        }

        private enum RequestState {
            SERVER_NAME_SRC, SERVER_NAME_DST, LOGIN_SRC, LOGIN_DST, PASSWORD, ADDRESS, NB_MEMBERS, SERVER_NAMES, ANYTHING
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