package main.java.server;

import main.java.OpCode;
import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
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
    private static final int BUFFER_SIZE = 8096;
    private static final Logger logger = Logger.getLogger(ServerChatFusion.class.getName());
    private static final long TIMEOUT = 60_000;
    private final String serverName;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Thread console;
    private final StateFusionController stateController = new StateFusionController();
    private final HashMap<SelectionKey, String> clientConnected = new HashMap<>();

    private final HashMap<String, SelectionKey> clientNameConnected = new HashMap<>();
    private final ConcurrentHashMap<SelectionKey, String> serverConnected = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SelectionKey> serverNameConnected = new ConcurrentHashMap<>();
    private final List<String> memberAddList = new ArrayList<>();
    private SocketChannel leaderSocketChannel = SocketChannel.open();
    private SocketChannel actualConnectionSocketChannel = SocketChannel.open();
    private boolean isLeader = true;
    private FusionState fusionState = FusionState.IDLE;
    private Context leader;
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

    /**
     * Determines if the {@link ServerChatFusion} is the leader of the Mega-Server
     * @return true if the {@link ServerChatFusion} is the leader, otherwise false
     */
    private boolean isLeader() {
        return isLeader;
    }

    /**
     * Makes the {@link ServerChatFusion} contained in the {@link Context} become the leader of the Mega-Server
     * @param context containing the new leader of the Mega-Server
     */
    private void setLeader(Context context) {
        this.leader = context;
        isLeader = false;
    }

    /**
     * Determines if the Object o is equals to the {@link ServerChatFusion}
     * @param o the Object to be compared
     * @return true if they are equals, otherwise false
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof ServerChatFusion that && Objects.equals(serverSocketChannel, that.serverSocketChannel) && Objects.equals(selector, that.selector) && Objects.equals(console, that.console) && Objects.equals(stateController, that.stateController) && Objects.equals(leader, that.leader) && Objects.equals(clientConnected, that.clientConnected);
    }

    /**
     * Returns a hashCode based on the content of the {@link ServerChatFusion}
     * @return the hashCode generated
     */
    @Override
    public int hashCode() {
        return Objects.hash(serverSocketChannel, selector, console, stateController, leader, clientConnected);
    }

    /**
     * Prints the different commands usable on the {@link ServerChatFusion}
     */
    private void printConsoleUsage() {
        System.out.println("""
                List of commands:
                    - INFO -> Print the number of server and client connected
                    - SHUTDOWN -> Stop any server or client to connect to this server
                    - SHUTDOWNNOW -> End the server and its connections
                    - FUSION [server_address] [port] ->  (FUSION 127.0.0.1 7777) - Ask a fusion to the server corresponding to the given address and port
                """);
    }

    /**
     * Runs the commands selected in the console of the {@link ServerChatFusion}
     */
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
                    case "/help" -> printConsoleUsage();

                    case String msgString && (msgString.startsWith("FUSION")) ->
                            stateController.sendCommand(msgString.substring(7), selector);

                    default -> {
                    }
                }
            }
        }
        logger.info("Console thread stopping");
    }

    /**
     * Launches the {@link ServerChatFusion}
     * @throws IOException If an I/O error occurs
     */
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
        if (!isLeader()) {
            leader.queueRequest(RequestFactory.fusionRequest(remoteServer));
            return;
        }

        if (actualConnection != null) {
            actualConnection.silentlyClose();
        }

        updateConnection(actualConnectionSocketChannel, remoteServer);

        var key = actualConnectionSocketChannel.register(selector, SelectionKey.OP_CONNECT);
        actualConnection = new Context(this, key);
        key.attach(actualConnection);

        String[] names = serverNameConnected.keySet().stream().toList().toArray(new String[0]);
        var request = RequestFactory.fusionInit(serverName, (InetSocketAddress) serverSocketChannel.getLocalAddress(), serverConnected.size(), names);
        ((Context) key.attachment()).queueRequest(request);
        fusionState = FusionState.PENDING_FUSION;
    }

    /**
     * Checks the state of the {@link SelectionKey} and executes methods consequently
     * @param key the {@link SelectionKey} used
     */
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

    /**
     * Makes the {@link ServerChatFusion} accepts the connection with the {@link SelectionKey}
     * @param key the key to be accepted by the {@link ServerChatFusion}
     * @throws IOException If an I/O error occurs
     */
    private void doAccept(SelectionKey key) throws IOException {
        var sc = ((ServerSocketChannel) key.channel()).accept();
        if (sc == null) {
            return;
        }

        sc.configureBlocking(false);
        var sk = sc.register(selector, SelectionKey.OP_READ);
        sk.attach(new Context(this, sk));

    }

    /**
     * Closes the connection with the {@link SelectionKey}
     * @param key the {@link SelectionKey} disconnected
     */
    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        if (sc instanceof ServerSocketChannel) {
            System.out.println("\tKey for ServerSocketChannel :");
        } else {
            System.out.println("\tKey for Client ");
        }
        var serverRemoved = serverConnected.remove(key);
        if (serverRemoved != null) serverNameConnected.remove(serverRemoved);
        var clientRemoved = clientConnected.remove(key);
        if (clientRemoved != null) clientNameConnected.remove(clientRemoved);

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

    /**
     * Redirects a private message form the sender to a {@link main.java.client.ClientChatFusion}
     * @param request the {@link Request} sent by the sender
     * @param sender the sender of the private message
     */
    private void messagePrivate(RequestMessagePrivate request, SelectionKey sender) {
        String serverDst = request.serverDst().string();
        String loginDst = request.loginDst().string();
        SelectionKey key;
        var optClient = clientConnected.entrySet().stream().filter(entry -> entry.getValue().equals(loginDst)).findFirst();
        if (optClient.isPresent()) {
            key = optClient.get().getKey();
            ((Context) key.attachment()).queueRequest(request);
        } else {
            if (isLeader()) {
                System.out.println(serverConnected.values());
                var optServer = serverConnected.entrySet().stream().filter(entry -> entry.getValue().equals(serverDst)).findFirst();
                System.out.println(serverDst);
                if (optServer.isPresent()) {
                    key = optServer.get().getKey();
                    ((Context) key.attachment()).queueRequest(request);
                }
                return;
            }
            if (!sender.equals(leader.key)) {
                leader.queueRequest(request);
            }
        }
    }


    /**
     * Adds a {@link main.java.client.ClientChatFusion} to the clientConnected List
     * @param login the login of the new {@link main.java.client.ClientChatFusion}
     * @param key the {@link SelectionKey} associated to the new {@link main.java.client.ClientChatFusion}
     */
    public void addClient(String login, SelectionKey key) {
        var refused = login.getBytes(StandardCharsets.UTF_8).length > 30;
        var client = (Context) key.attachment();
        if (!refused) {
            refused = (clientNameConnected.put(login, key) != null);
        }

        if (refused) {
            // send connection refused
            client.queueRequest(RequestFactory.loginRefused());
            return;
        }

        clientConnected.putIfAbsent(key, login);
        logger.info("Client " + login + " connected");
        // send connection accept
        client.queueRequest(RequestFactory.loginAccepted(serverName));
    }

    /**
     * Redirects the private file sent to its destination
     * @param requestMessageFilePrivate the {@link Request} containing the private file
     */
    private void redirectFilePrivate(RequestMessageFilePrivate requestMessageFilePrivate) {
        var serverDst = requestMessageFilePrivate.serverDst().string();
        if (serverName.equals(serverDst)) {
            var client = clientNameConnected.get(requestMessageFilePrivate.loginDst().string());
            // Request ignored if the client doest exist
            if (client != null) {
                ((Context) client.attachment()).queueRequest(requestMessageFilePrivate);
            }
            return;
        }


        if (isLeader()) {
            var serverKey = this.serverNameConnected.get(serverDst);
            // ignored if the server doest exist
            if (serverKey != null) {
                ((Context) serverKey.attachment()).queueRequest(requestMessageFilePrivate);
            }
            return;
        }

        this.leader.queueRequest(requestMessageFilePrivate);
    }

    /**
     * Handles the FusionInit operation
     * @param requestFusionInit the {@link Request} containing the FusionInit
     * @param keyServer the {@link SelectionKey} containing the {@link ServerChatFusion} initiating the merge
     * @throws IOException If an I/O error occurs
     */
    private void handleFusionInit(RequestFusionInit requestFusionInit, SelectionKey keyServer) throws IOException {
        var otherServerName = requestFusionInit.serverName().string();
        var otherServerAddress = requestFusionInit.address().address();

        if (this.serverName.equals(otherServerName) || this.serverNameConnected.contains(otherServerName)) {
            ((Context) keyServer.attachment()).queueRequest(RequestFactory.fusionInitKO());
            return;
        }

        logger.info("Fusion accepted with: " + otherServerName + ":" + otherServerAddress + " :: " + requestFusionInit.nbMembers() + " :: " + requestFusionInit.names());

        String[] names = this.serverNameConnected.keySet().stream().toList().toArray(new String[0]);
        ((Context) keyServer.attachment()).queueRequest(RequestFactory.fusionInitOK(this.serverName, (InetSocketAddress) this.serverSocketChannel.getLocalAddress(), this.serverConnected.size(), names));

        updateLeader(otherServerName, otherServerAddress, keyServer);
    }

    /**
     * Handles the FusionInitRequest operation
     * @param requestFusionRequest the {@link Request} containing the FusionInitRequest
     * @param serverKey the {@link SelectionKey} containing the {@link ServerChatFusion} asking the merge
     */
    private void handleFusionRequest(RequestFusionRequest requestFusionRequest, SelectionKey serverKey) {
        if (this.fusionState == FusionState.PENDING_FUSION) {
            ((Context) serverKey.attachment()).queueRequest(RequestFactory.fusionRequestRefused());
            return;
        }
        var address = requestFusionRequest.address().address();
        var host = address.getHostName();
        var port = address.getPort();
        this.stateController.sendCommand(host + " " + port, this.selector);
        ((Context) serverKey.attachment()).queueRequest(RequestFactory.fusionRequestAccepted());
    }

    /**
     * If the given server becomes the new leader, then update every client and the leader of the actual server
     * Otherwise, do nothing
     *
     * @param otherServerName    given sevrer
     * @param otherServerAddress address of the given server
     * @param otherServerKey     SelectionKey of the otherServer
     */
    private void updateLeader(String otherServerName, InetSocketAddress otherServerAddress, SelectionKey otherServerKey) throws IOException {
        if (otherServerName.compareTo(serverName) < 0) {
            var newLeader = (Context) otherServerKey.attachment();
            leaderSocketChannel = actualConnectionSocketChannel;
            setLeader(newLeader);
            serverConnected.keySet().stream().map(key -> (Context) otherServerKey.attachment()).forEach(server -> server.queueRequest(RequestFactory.fusionChangeLeader(otherServerAddress)));
            serverConnected.clear();
            serverNameConnected.clear();
            fusionState = FusionState.IDLE;
            System.out.println("We change leader to: " + otherServerName);
        } else {
            serverConnected.put(otherServerKey, otherServerName);
            serverNameConnected.put(otherServerName, otherServerKey);
            System.out.println("We stay leader");
        }

        actualConnectionSocketChannel = SocketChannel.open();
        actualConnectionSocketChannel.configureBlocking(false);
        actualConnection = null;
    }

    /**
     * Handles the FusionInitFWD operation
     * @param requestFusionInitFWD the {@link Request} containing the FusionInitFWD
     */
    private void handleForwardingFusion(RequestFusionInitFWD requestFusionInitFWD) throws IOException {
        var leaderAddress = requestFusionInitFWD.address().address();

        updateConnection(actualConnectionSocketChannel, leaderAddress);

        var key = actualConnectionSocketChannel.register(selector, SelectionKey.OP_CONNECT);
        actualConnection = new Context(this, key);
        key.attach(actualConnection);

        String[] names = serverNameConnected.keySet().stream().toList().toArray(new String[0]);
        actualConnection.queueRequest(RequestFactory.fusionInit(serverName, (InetSocketAddress) serverSocketChannel.getLocalAddress(), serverConnected.size(), names));
    }

    /**
     * Updates the connection between the {@link ServerChatFusion} and the old connected {@link ServerChatFusion} connecting with the new one
     * @param sc the {@link SocketChannel} of the new connected {@link ServerChatFusion}
     * @param address the {@link InetSocketAddress} of the new connected {@link ServerChatFusion}
     * @throws IOException IOException If an I/O error occurs
     */
    private void updateConnection(SocketChannel sc, InetSocketAddress address) throws IOException {
        actualConnectionSocketChannel = SocketChannel.open();
        actualConnectionSocketChannel.configureBlocking(false);
        actualConnectionSocketChannel.connect(address);
    }

    /**
     * Changes the {@link ServerChatFusion} leader of the Mega-Server
     * @param fusionChangeLeader the new leader of the Mega-Server
     * @throws IOException IOException If an I/O error occurs
     */
    private void handleChangeLeader(RequestFusionChangeLeader fusionChangeLeader) throws IOException {
        updateConnection(leaderSocketChannel, fusionChangeLeader.address().address());

        var key = leaderSocketChannel.register(selector, SelectionKey.OP_CONNECT);
        leader = new Context(this, key);
        key.attach(leader);

        leader.queueRequest(RequestFactory.fusionMerge(serverName));
    }

    /**
     * Handles the FusionMerge operation
     * @param requestFusionMerge the {@link Request} containing the FusionMerge operation
     * @param serverKey the {@link SelectionKey} containing the new {@link ServerChatFusion} to connect
     */
    private void handleFusionMerge(RequestFusionMerge requestFusionMerge, SelectionKey serverKey) {
        if (this.memberAddList.remove(requestFusionMerge.serverName().string())) {
            this.serverConnected.put(serverKey, requestFusionMerge.serverName().string());
            this.serverNameConnected.put(requestFusionMerge.serverName().string(), serverKey);
        }

        if (this.memberAddList.isEmpty()) {
            this.fusionState = FusionState.IDLE;
        }
    }

    /**
     * The different states of the server commands
     */
    private enum State {
        WORKING, STOP_ACCEPTING, SHUTDOWN
    }

    /**
     * The different states of the fusion operation
     */
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
        private final ArrayDeque<Request> requestQueue = new ArrayDeque<>();
        private final ServerChatFusion server; // we could also have Context as an instance class
        private ReadingState readingState = ReadingState.WAITING_FOR_REQUEST;
        private boolean activeSinceLastTimeoutCheck = true;
        private boolean closed = false;
        private Reader<Request> requestReader;

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
            while (bufferIn.position() != 0) {
                if (readingState == ReadingState.WAITING_FOR_REQUEST) {
                    byteOpCodeReader();
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
                        readingState = ReadingState.WAITING_FOR_REQUEST;
                        logger.severe("Error reading, closing connection");
                    }
                }
            }


        }

        /**
         * Reads the OpCode containing in the {@link ByteBuffer} bufferIn
         */
        private void byteOpCodeReader() {
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

        /**
         * Handle the {@link Request} to read its content and act consequently
         * @param request The {@link Request} to handle
         * @throws IOException
         */
        private void requestHandler(Request request) throws IOException {
            switch (request) {
                case RequestLoginAnonymous requestLoginAnonymous ->
                        server.addClient(requestLoginAnonymous.login().string(), key);

                case RequestLoginPassword requestLoginPassword ->
                        server.addClient(requestLoginPassword.login().string(), key);

                case RequestMessagePublic requestMessagePublic -> server.broadcast(requestMessagePublic, key);

                case RequestMessagePrivate requestMessagePrivate -> server.messagePrivate(requestMessagePrivate, key);

                case RequestMessageFilePrivate requestMessageFilePrivate ->
                        server.redirectFilePrivate(requestMessageFilePrivate);

                case RequestFusionInit requestFusionInit -> server.handleFusionInit(requestFusionInit, key);

                case RequestFusionInitKO ignored -> {
                    logger.info("Fusion denied !");
                    server.fusionState = FusionState.IDLE;
                }

                case RequestFusionInitOK requestFusionInitOK -> {
                    logger.info("Fusions Init OK From: " + requestFusionInitOK.serverName().string() + ":" + requestFusionInitOK.address().address() + " :: " + requestFusionInitOK.nbMembers() + " :: " + requestFusionInitOK.names());
                    server.updateLeader(requestFusionInitOK.serverName().string(), requestFusionInitOK.address().address(), key);
                }

                case RequestFusionRequest requestFusionRequest -> server.handleFusionRequest(requestFusionRequest, key);

                case RequestFusionInitFWD requestFusionInitFWD -> server.handleForwardingFusion(requestFusionInitFWD);

                case RequestFusionRequestResponse requestFusionRequestResponse -> {
                    if (requestFusionRequestResponse.status() == 0)
                        System.out.println("Fusion has been refused by the leader");
                    else System.out.println("Fusion has been accepted by the leader");
                }

                case RequestFusionChangeLeader fusionChangeLeader -> server.handleChangeLeader(fusionChangeLeader);

                case RequestFusionMerge requestFusionMerge -> {
                    server.handleFusionMerge(requestFusionMerge, key);
                }

                default -> { // Unsupported request, we end the connection with the client
                    logger.severe("Unsupported request:" + request);
                    silentlyClose();
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
                if (bufferOut.remaining() < requestQueue.peek().bufferLength()) {
                    return;
                }

                var request = requestQueue.poll();
                // If there is a fusion request while the server is pending a fusion,
                // then it's dismissed waiting for the current fusion to finish by putting
                // the request to the last request of the queue
                switch (request) {
                    case RequestFusionInit ignored && server.fusionState == FusionState.PENDING_FUSION ->
                            requestQueue.add(request);

                    default -> bufferOut.put(request.encode());
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

        /**
         * Close the {@link Context} if its inactive
         */
        public void closeIfInactive() {
            if (!activeSinceLastTimeoutCheck) {
                silentlyClose();
            }

            // disabled for now
            activeSinceLastTimeoutCheck = true;
        }

        /**
         * Close the {@link Context}
         */
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
            processOut();
            updateInterestOps();
        }

        /**
         * Connect the {@link SocketChannel} associated with the {@link Context}
         * @throws IOException If some I/O exception occurs
         */
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

        /**
         * Prints the content of the {@link SelectionKey} pasted as argument
         * @param key the {@link SelectionKey} to read
         */
        static void printSelectedKey(SelectionKey key) {
            var channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
            } else {
                var sc = (SocketChannel) channel;
                System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
            }
        }

        /**
         * Returns a {@link String} containing the action to do for the {@link SelectionKey}
         * @param key the {@link SelectionKey} to read
         * @return the {@link String} containing the action to do
         */
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