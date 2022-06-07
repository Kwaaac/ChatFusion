package main.java.server;

import main.java.OpCode;
import main.java.Utils.RequestFactory;
import main.java.reader.*;
import main.java.request.*;
import main.java.request.Request.ReadingState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatFusion {
    private static final int BUFFER_SIZE = 4048;
    private static final Logger logger = Logger.getLogger(ServerChatFusion.class.getName());
    private static final long TIMEOUT = 60_000;
    private final String serverName;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Thread console;
    private final StateFusionController stateController = new StateFusionController();
    private final HashMap<SelectionKey, String> clientConnected = new HashMap<>();
    private final ConcurrentHashMap<SelectionKey, String> serverConnected = new ConcurrentHashMap<>();
    private final List<String> memberAddList = new ArrayList<>();
    private SocketChannel leaderSocketChannel = SocketChannel.open();
    private SocketChannel actualConnectionSocketChannel = SocketChannel.open();
    private boolean isLeader = true;
    private FusionState fusionState = FusionState.IDLE;
    private Context leader;

    private InetSocketAddress leaderAddress;
    private Context actualConnection;

    public ServerChatFusion(String serverName, InetSocketAddress socketAddress) throws IOException {
        Objects.requireNonNull(serverName);
        if (serverName.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.serverName = serverName;
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(socketAddress);

        leaderSocketChannel.configureBlocking(false);
        actualConnectionSocketChannel.configureBlocking(false);

        selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        new ServerChatFusion(args[0], new InetSocketAddress("127.0.0.1", Integer.parseInt(args[1]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumBetter serverName port");
    }

    private boolean isLeader() {
        return isLeader;
    }

    private void setLeader(Context context) {
        this.leader = context;
        isLeader = false;
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
                    case "INFO" -> System.out.println((selector.keys().size() - 1) + " Server and Client connected");

                    case "SHUTDOWN" -> stateController.updateState(State.STOP_ACCEPTING);

                    case "SHUTDOWNNOW" -> {
                        stateController.updateState(State.SHUTDOWN);
                        selector.wakeup();
                    }
                    case String msgString && msgString.startsWith("FUSION") ->
                            stateController.sendCommand(msgString.substring(7), selector);

                    default -> {
                    }
                }
            }
        }
        logger.info("Console thread stopping");
    }

    public void launch() throws IOException {
        System.out.println(serverSocketChannel.getLocalAddress());
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
        var remoteServer = new InetSocketAddress(commands[0], Integer.parseInt(commands[1]));
        // FIXME Passer le RequestFactory FusionRequest avec un Request et non un RecordRequest (qui va disparaitre)
        /*
        if (!isLeader()) {
            leader.queueRequest(RequestFactory.fusionRequest(remoteServer));
            return;
        }
        */
        if (actualConnection != null) {
            actualConnection.silentlyClose();
        }

        actualConnectionSocketChannel = SocketChannel.open();
        actualConnectionSocketChannel.configureBlocking(false);

        actualConnectionSocketChannel.connect(remoteServer);

        var key = actualConnectionSocketChannel.register(selector, SelectionKey.OP_CONNECT);
        actualConnection = new Context(this, key);
        key.attach(actualConnection);

        String[] names = serverConnected.values().stream().toList().toArray(new String[0]);
        // FIXME Passer le RequestFactory avec un Request et non un RecordRequest (qui va disparaitre)
        /*
        ((Context) key.attachment()).queueRequest(RequestFactory.fusionInit(serverName, (InetSocketAddress) serverSocketChannel.getLocalAddress(), serverConnected.size(), names));
        */
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

    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        if (sc instanceof ServerSocketChannel) {
            System.out.println("\tKey for ServerSocketChannel :");
        } else {
            System.out.println("\tKey for Client ");
        }
        serverConnected.remove(key);
        clientConnected.remove(key);
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
        clientConnected.keySet().stream().map(key -> (Context) key.attachment()).forEach(context -> context.queueRequest(request));
        if (isLeader()) {
            serverConnected.keySet().stream().filter(s -> !s.equals(sender)).map(s -> (Context) s.attachment()).forEach(context -> context.queueRequest(request));
            return;
        }

        if (!sender.equals(leader.key)) {
            leader.queueRequest(request);
        }
    }

    public void addClient(String login, SelectionKey key) {
        var refused = login.getBytes(StandardCharsets.UTF_8).length <= 30;
        var client = (Context) key.attachment();
        if (!refused) {
            refused = (clientConnected.putIfAbsent(key, login) != null);
        }

        if (refused) {
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
        public void sendCommand(String msg, Selector selector) {
            synchronized (lock) {
                requestFusionQueue.add(msg);
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
        private ReadingState readingState = ReadingState.WAITING_FOR_REQUEST;
        private RequestState requestState = RequestState.ANYTHING;
        private boolean activeSinceLastTimeoutCheck = true;
        private boolean closed = false;
        private int index_members;
        private int nbMembers;
        private String serverSrc;
        private InetSocketAddress address;

        private Reader<Request> requestReader;

        private Context(ServerChatFusion server, SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * If the given server becomes the new leader, then update every client and the leader of the actual server
         * Otherwise, do nothing
         *
         * @param serverName    given sevrer
         * @param serverAddress address of the given server
         */
        private void updateLeader(String serverName, InetSocketAddress serverAddress) throws IOException {
            if (serverName.compareTo(server.serverName) < 0) {
                var newLeader = (Context) key.attachment();
                server.leaderSocketChannel = server.actualConnectionSocketChannel;
                server.setLeader(newLeader);
                server.leaderAddress = serverAddress;
                // FIXME Passer le RequestFactory FusionRequest avec un Request et non un RecordRequest (qui va disparaitre)
                // server.serverConnected.keySet().stream().map(key -> (Context) key.attachment()).forEach(server -> server.queueRequest(RequestFactory.fusionChangeLeader(serverAddress)));
                server.serverConnected.clear();
                server.fusionState = FusionState.IDLE;
            } else {
                server.serverConnected.put(key, serverSrc);
            }
            server.actualConnectionSocketChannel = SocketChannel.open();
            server.actualConnectionSocketChannel.configureBlocking(false);
            server.actualConnection = null;
        }


        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() throws IOException {
            if (readingState == ReadingState.WAITING_FOR_REQUEST) {
                bufferIn.flip();
                var optionalOpCode = OpCode.getOpCodeFromByte(bufferIn.get());
                bufferIn.compact();
                if (optionalOpCode.isPresent()) {
                    requestReader = optionalOpCode.get().getRequestReader();
                    readingState = ReadingState.READING_REQUEST;
                } else {
                    // Close the connection if it sent a wrong OpCode
                    silentlyClose();
                    logger.severe("Wrong opCode were read, closing connection");
                }
            }

            // Read the request
            var status = requestReader.process(bufferIn);
            switch (status) {
                case DONE -> {
                    Request request = requestReader.get();
                    requestHandler(request);
                    readingState = ReadingState.WAITING_FOR_REQUEST;
                }
                case REFILL -> {
                }
                case ERROR -> {
                    silentlyClose();
                    logger.severe("Error reading, closing connection");
                }
            }

                /*
                switch (watcher) {
                    case LOGIN_ANONYMOUS, LOGIN_PASSWORD -> {
                        var reader = new RequestLoginAnonymousReader();
                        var status = reader.process(bufferIn);
                        switch (status) {
                            case DONE -> {
                                var request = (RequestLoginAnonymous) reader.get();
                                // ici on renverrai seulement la request, puis un futur pattern matching gère le cas de cette request en particulier (pas besoin de cast)
                                server.addClient(request.login().string(), key);
                                reset();
                            }
                            case ERROR -> {
                                queueRequest(RequestFactory.loginRefused());
                                reset();
                                return;
                            }
                            case REFILL -> {
                                return;
                            }
                        }
                    }

                    case MESSAGE -> {
                        // Fetch server serverName
                        if (requestState == RequestState.ANYTHING) {
                            var serverStatus = stringReader.process(bufferIn);
                            switch (serverStatus) {
                                case DONE -> {
                                    serverSrc = stringReader.get();
                                    stringReader.reset();
                                    requestState = RequestState.LOGIN_SRC;
                                }
                                case ERROR -> {
                                    //Message ignored
                                    stringReader.reset();
                                    reset();
                                    return;
                                }
                                case REFILL -> {
                                    return;

                                }
                            }
                        }
                        // Fetch login & message
                        if (requestState == RequestState.LOGIN_SRC) {
                            var loginStatus = messageReader.process(bufferIn);
                            switch (loginStatus) {
                                case DONE -> {
                                    var message = messageReader.get();
                                    messageReader.reset();
                                    var request = RequestFactory.publicMessage(serverSrc, message);
                                    server.broadcast(request, key);
                                    reset();
                                }
                                case ERROR -> {
                                    // Message ignored
                                    messageReader.reset();
                                    reset();
                                    return;
                                }
                                case REFILL -> {
                                    return;
                                }
                            }
                        }
                    }

                    case FUSION_INIT -> {
                        if (!server.isLeader()) {
                            ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitForward(server.leaderAddress));
                            reset();
                            return;
                        }

                        if (fusionInit()) return;

                        if (requestState == RequestState.SERVER_NAMES) {
                            for (; index_members < nbMembers; index_members++) {
                                var memberStatus = stringReader.process(bufferIn);
                                switch (memberStatus) {
                                    case DONE -> {
                                        var member = stringReader.get();
                                        if (server.clientConnected.containsValue(member)) {
                                            ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitKO());
                                            reset();
                                            return;
                                        }
                                        stringReader.reset();
                                        server.memberAddList.add(member);
                                    }
                                    case ERROR -> {
                                        stringReader.reset();
                                        reset();
                                        return;
                                    }

                                    case REFILL -> {
                                        return;
                                    }
                                }
                            }
                            System.out.println("Fusion accepted with: " + serverSrc + ":" + address + " :: " + nbMembers + " :: " + server.memberAddList);

                            String[] names = server.serverConnected.values().stream().toList().toArray(new String[0]);
                            ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitOK(server.serverName, (InetSocketAddress) server.serverSocketChannel.getLocalAddress(), server.serverConnected.size(), names));

                            updateLeader(serverSrc, address);

                            reset();
                        }
                    }

                    case FUSION_INIT_OK -> {
                        // This server is the leader
                        if (fusionInit()) return;

                        if (requestState == RequestState.SERVER_NAMES) {
                            for (; index_members < nbMembers; index_members++) {
                                var memberStatus = stringReader.process(bufferIn);
                                switch (memberStatus) {
                                    case DONE -> {
                                        var member = stringReader.get();
                                        if (server.clientConnected.containsValue(member)) {
                                            ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitKO());
                                            return;
                                        }
                                        stringReader.reset();
                                        server.memberAddList.add(member);
                                    }
                                    case ERROR -> {
                                        watcher = OpCode.IDLE;
                                        requestState = RequestState.ANYTHING;
                                        stringReader.reset();
                                        return;
                                    }

                                    case REFILL -> {
                                        return;
                                    }
                                }
                            }

                            System.out.println("Fusions Init OK From: " + serverSrc + ":" + address + " :: " + nbMembers + " :: " + server.memberAddList);

                            updateLeader(serverSrc, address);

                            reset();
                        }
                    }

                    case FUSION_INIT_KO -> {
                        server.fusionState = FusionState.IDLE;
                        logger.info("Fusion denied !");
                        reset();
                    }

                    case FUSION_INIT_FWD -> {
                        var addressStatus = addressReader.process(bufferIn);
                        switch (addressStatus) {
                            case DONE -> {
                                address = addressReader.get();
                                addressReader.reset();

                                var host = address.getHostName();
                                var port = address.getPort();

                                server.actualConnectionSocketChannel = SocketChannel.open();
                                server.actualConnectionSocketChannel.configureBlocking(false);

                                server.actualConnectionSocketChannel.connect(address);

                                var key = server.actualConnectionSocketChannel.register(server.selector, SelectionKey.OP_CONNECT);
                                server.actualConnection = new Context(server, key);
                                key.attach(server.actualConnection);

                                String[] names = server.serverConnected.values().stream().toList().toArray(new String[0]);
                                server.actualConnection.queueRequest(RequestFactory.fusionInit(server.serverName, (InetSocketAddress) server.serverSocketChannel.getLocalAddress(), server.serverConnected.size(), names));
                                reset();
                            }
                            case ERROR -> {
                                addressReader.reset();
                                reset();
                                return;
                            }
                            case REFILL -> {
                                return;
                            }
                        }
                    }

                    case FUSION_REQUEST -> {
                        if (server.fusionState == FusionState.PENDING_FUSION) {
                            ((Context) key.attachment()).queueRequest(RequestFactory.fusionRequestRefused());
                            reset();
                            return;
                        }

                        var addressStatus = addressReader.process(bufferIn);
                        switch (addressStatus) {
                            case DONE -> {
                                address = addressReader.get();
                                addressReader.reset();

                                var host = address.getHostName();
                                var port = address.getPort();
                                server.stateController.sendCommand(host + " " + port, server.selector);
                                ((Context) key.attachment()).queueRequest(RequestFactory.fusionRequestAccepted());
                                reset();
                            }
                            case ERROR -> {
                                addressReader.reset();
                                reset();
                                return;
                            }
                            case REFILL -> {
                                return;
                            }
                        }
                    }

                    case FUSION_REQUEST_RESPONSE -> {
                        bufferIn.flip();
                        if (bufferIn.hasRemaining()) {
                            if (bufferIn.get() == 0) {
                                System.out.println("Fusion has been refused by the leader");
                            } else {
                                System.out.println("Fusion has been accepted by the leader");
                            }
                        }
                        bufferIn.compact();
                        reset();
                    }

                    case FUSION_MERGE -> {
                        var serverNameStatus = stringReader.process(bufferIn);
                        switch (serverNameStatus) {
                            case DONE -> {
                                var serverName = stringReader.get();
                                stringReader.reset();

                                // If the server is in the list to be added, we add it to the map
                                if (server.memberAddList.remove(serverName)) {
                                    server.serverConnected.put(key, serverName);
                                }

                                if (server.memberAddList.isEmpty()) {
                                    server.fusionState = FusionState.IDLE;
                                }
                                reset();
                            }
                            case ERROR -> {
                                stringReader.reset();
                                reset();
                                return;
                            }
                            case REFILL -> {
                                return;
                            }
                        }
                    }

                    case FUSION_CHANGE_LEADER -> {
                        var addressStatus = addressReader.process(bufferIn);
                        switch (addressStatus) {
                            case DONE -> {
                                address = addressReader.get();
                                addressReader.reset();

                                server.leaderSocketChannel = SocketChannel.open();
                                server.leaderSocketChannel.configureBlocking(false);
                                server.leaderSocketChannel.connect(address);

                                var key = server.leaderSocketChannel.register(server.selector, SelectionKey.OP_CONNECT);
                                server.leader = new Context(server, key);
                                key.attach(server.leader);

                                server.leader.queueRequest(RequestFactory.fusionMerge(server.serverName));
                            }
                            case ERROR -> {
                                addressReader.reset();
                                reset();
                            }
                            case REFILL -> {
                            }
                        }
                    }

                    default -> throw new UnsupportedOperationException();
                }
                */

        }

        private void requestHandler(Request request) throws IOException {
            switch (request) {
                case RequestLoginAnonymous requestLoginAnonymous ->
                        server.addClient(requestLoginAnonymous.login().string(), key);

                case RequestLoginPassword requestLoginPassword ->
                        server.addClient(requestLoginPassword.login().string(), key);

                case RequestMessagePublic requestMessagePublic -> server.broadcast(requestMessagePublic, key);

                case RequestMessageFilePrivate requestMessageFilePrivate ->
                        System.out.println("requestMessageFilePrivate");

                case RequestFusionInit requestFusionInit -> {
                    if (server.serverConnected.containsValue(requestFusionInit.serverName())) {
                        ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitKO());
                        reset();
                        return;
                    }
                    logger.info("Fusion accepted with: " + serverSrc + ":" + address + " :: " + nbMembers + " :: " + server.memberAddList);

                    String[] names = server.serverConnected.values().stream().toList().toArray(new String[0]);
                    ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitOK(server.serverName, (InetSocketAddress) server.serverSocketChannel.getLocalAddress(), server.serverConnected.size(), names));

                    updateLeader(serverSrc, address);
                }

                case RequestFusionInitKO requestFusionInitKO -> {
                    logger.info("Fusion denied !");
                    server.fusionState = FusionState.IDLE;
                }

                case RequestFusionInitOK requestFusionInitOK -> {
                    System.out.println("Fusions Init OK From: " + serverSrc + ":" + address + " :: " + nbMembers + " :: " + server.memberAddList);
                    updateLeader(serverSrc, address);
                }

                case RequestFusionRequest requestFusionRequest -> {
                    var host = address.getHostName();
                    var port = address.getPort();
                    server.stateController.sendCommand(host + " " + port, server.selector);
                    ((Context) key.attachment()).queueRequest(RequestFactory.fusionRequestAccepted());
                }

                case RequestFusionInitFWD requestFusionInitFWD -> {
                    var host = address.getHostName();
                    var port = address.getPort();

                    server.actualConnectionSocketChannel = SocketChannel.open();
                    server.actualConnectionSocketChannel.configureBlocking(false);

                    server.actualConnectionSocketChannel.connect(address);

                    var key = server.actualConnectionSocketChannel.register(server.selector, SelectionKey.OP_CONNECT);
                    server.actualConnection = new Context(server, key);
                    key.attach(server.actualConnection);

                    String[] names = server.serverConnected.values().stream().toList().toArray(new String[0]);
                    server.actualConnection.queueRequest(RequestFactory.fusionInit(server.serverName, (InetSocketAddress) server.serverSocketChannel.getLocalAddress(), server.serverConnected.size(), names));
                }

                case RequestFusionRequestResponse requestFusionRequestResponse -> {
                    if (requestFusionRequestResponse.status() == 0)
                        System.out.println("Fusion has been refused by the leader");
                    else
                        System.out.println("Fusion has been accepted by the leader");
                }

                case RequestFusionChangeLeader fusionChangeLeader -> {
                    server.leaderSocketChannel = SocketChannel.open();
                    server.leaderSocketChannel.configureBlocking(false);
                    server.leaderSocketChannel.connect(fusionChangeLeader.address().address());

                    var key = server.leaderSocketChannel.register(server.selector, SelectionKey.OP_CONNECT);
                    server.leader = new Context(server, key);
                    key.attach(server.leader);

                    server.leader.queueRequest(RequestFactory.fusionMerge(server.serverName));
                }

                default -> { // Unsupported request, we end the connection with the client
                    logger.severe("Unsupported request:" + request);
                    silentlyClose();
                }
            }
        }

        /*
                private boolean fusionInit() {
                    if (requestState == RequestState.ANYTHING) {
                        var serverStatus = stringReader.process(bufferIn);
                        switch (serverStatus) {
                            case DONE -> {
                                serverSrc = stringReader.get();
                                stringReader.reset();
                                if (server.serverConnected.containsValue(serverSrc)) {
                                    ((Context) key.attachment()).queueRequest(RequestFactory.fusionInitKO());
                                    reset();
                                    return true;
                                }

                                requestState = RequestState.ADDRESS;
                            }
                            case ERROR -> {
                                stringReader.reset();
                                reset();
                                return true;
                            }
                            case REFILL -> {
                                return true;
                            }
                        }
                    }

                    if (requestState == RequestState.ADDRESS) {
                        var addressStatus = addressReader.process(bufferIn);
                        switch (addressStatus) {
                            case DONE -> {
                                address = addressReader.get();
                                addressReader.reset();
                                requestState = RequestState.NB_MEMBERS;
                            }
                            case ERROR -> {
                                addressReader.reset();
                                reset();

                                return true;
                            }
                            case REFILL -> {
                                return true;
                            }
                        }
                    }

                    if (requestState == RequestState.NB_MEMBERS) {

                        var nbMembersStatus = intReader.process(bufferIn);
                        switch (nbMembersStatus) {
                            case DONE -> {
                                nbMembers = intReader.get();
                                intReader.reset();

                                requestState = RequestState.SERVER_NAMES;
                                index_members = 0;
                            }
                            case ERROR -> {
                                intReader.reset();
                                reset();

                                return true;
                            }
                            case REFILL -> {
                                return true;
                            }
                        }
                    }
                    return false;
                }
        */
        private void reset() {
            requestState = RequestState.ANYTHING;
            readingState = ReadingState.WAITING_FOR_REQUEST;
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
                    /*
                    if (request.code() == OpCode.FUSION_INIT && server.fusionState == FusionState.PENDING_FUSION) {
                        requestQueue.add(request);
                        return;
                    }
                     */
                    bufferOut.put(request.encode());
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
            if (key.interestOps() == SelectionKey.OP_CONNECT) {
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
         * @throws IOException If some I/O exception occurs
         */
        private void doRead() throws IOException {
            activeSinceLastTimeoutCheck = true;
            closed = (sc.read(bufferIn) == -1);
            if (!closed) {
                processIn();
            }
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException If some I/O exception occurs
         */
        private void doWrite() throws IOException {
            activeSinceLastTimeoutCheck = true;
            sc.write(bufferOut.flip());
            bufferOut.compact();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) return; // the selector gave a bad hint
            updateInterestOps();
        }

        private enum RequestState {
            SERVER_NAME_SRC, SERVER_NAME_DST, LOGIN_SRC, LOGIN_DST, PASSWORD, ADDRESS, NB_MEMBERS, SERVER_NAMES, ANYTHING
        }
    }

    static class Helpers {
        /***
         * Theses methods are here to help understanding the behavior of the selector
         ***/
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