package main.java.client;

import main.java.OpCode;
import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.request.*;
import main.java.request.Request.ReadingState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

public class ClientChatFusion {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    static private final int BUFFER_SIZE = 8096;
    static private final Logger logger = Logger.getLogger(ClientChatFusion.class.getName());
    private final SocketChannel sc;
    private final SendThreadSafe sendThreadSafe = new SendThreadSafe();
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final String login;
    private final Thread console;
    private String password;
    private Context uniqueContext;
    private String transfertDir;

    public ClientChatFusion(String login, InetSocketAddress serverAddress, String transfertDir) throws IOException {
        this.serverAddress = serverAddress;

        if (!Files.isDirectory(Path.of(transfertDir))) {
            throw new IllegalArgumentException("transfert_directory argument should be a directory");
        }
        if (login.getBytes(UTF8).length > 30) {
            throw new IllegalArgumentException("Login length should be less than 30 bytes");
        }

        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        this.transfertDir = transfertDir;
        console.setDaemon(true);
    }

    public ClientChatFusion(String login, String password, InetSocketAddress serverAddress, String transfertDir) throws IOException {
        this(login, serverAddress, transfertDir);
        if (password.getBytes(UTF8).length > 30) {
            throw new IllegalArgumentException("Password length should be less than 30 bytes");
        }
        this.password = password;

    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        System.out.println("args = " + Arrays.toString(args));
        if (args.length != 4 && args.length != 5) {
            usage();
            return;
        }
        if (args.length == 5) {
            new ClientChatFusion(args[2], args[3], new InetSocketAddress(args[0], Integer.parseInt(args[1])), args[4]).launch();
            return;
        }

        new ClientChatFusion(args[2], new InetSocketAddress(args[0], Integer.parseInt(args[1])), args[3]).launch();
    }

    private static void usage() {
        System.out.println("""
                Usages :
                Login anonymous: java ClientChat [hostname] [port] [login] [transfert_directory]
                                
                Login with password: java ClientChat [hostname] [port] [login] [password] [transfert_directory]
                """);
    }

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendThreadSafe.sendCommand(msg, selector);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = password == null ? new Context(key, login, transfertDir) : new Context(key, login, password, transfertDir);
        key.attach(uniqueContext);
        sc.connect(serverAddress);
        console.start();
        while (!Thread.interrupted() && !uniqueContext.closed) {
            try {
                selector.select(this::treatKey);
                processCommand();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void printConsoleUsage() {
        System.out.println("""
                List of commands:
                    - /help -> print this usage section
                    - /w -> whisper a private message to a client
                    - /wf [server_destination_name] [login_client] [filename_in_the_transfert_directory]-> whisper a private file to a client
                """);
    }

    /**
     * Processes the command from the BlockingQueue
     */
    private void processCommand() throws IOException {
        var msg = sendThreadSafe.processCommand();
        if (msg == null) {
            return;
        }

        switch (msg) {
            case String msgString && msgString.startsWith("/help") -> printConsoleUsage();
            case String msgString && msgString.startsWith("/wf") -> {
                var commands = msgString.split(" ");
                System.out.println("commands = " + Arrays.toString(commands));
                var serverDst = commands[1];
                var loginDst = commands[2];
                System.out.println("transfertDir = " + transfertDir);
                var filepath = Path.of(transfertDir, commands[3]);

                var fileChatFusion = FileChatFusion.initToSend(filepath);
                var nbBlocksMax = fileChatFusion.getNbBlocksMax();
                for (int i = 0; i < nbBlocksMax; i++) {
                    var block = fileChatFusion.write();
                    uniqueContext.sendFilePrivate(login, serverDst, loginDst, filepath.getFileName().toString(), nbBlocksMax, block.length, block);

                }
            }
            case String msgString && msgString.startsWith("/w") ->
                    System.out.println("TODO"); // TODO uniqueContext.sendPrivateMessage(...);

            default -> uniqueContext.sendPublicMessage(login, msg);
        }
    }


    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    private enum State {
        CONNECTED, PENDING_ANONYMOUS, PENDING_PASSWORD
    }

    private static class SendThreadSafe {
        private final Object lock = new Object();
        private final BlockingQueue<String> msgQueue = new ArrayBlockingQueue<>(10);

        /**
         * Send instructions to the selector via a BlockingQueue and wake it up
         */
        public void sendCommand(String msg, Selector selector) throws InterruptedException {
            synchronized (lock) {
                msgQueue.add(msg);
                selector.wakeup();
            }
        }

        public String processCommand() {
            synchronized (lock) {
                return msgQueue.poll();
            }
        }
    }

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final String login;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Request> requestQueue = new ArrayDeque<>();
        private final ArrayDeque<Request> fileRequestQueue = new ArrayDeque<>();
        private final Map<String, FileChatFusion> mapFile = new HashMap<>();
        private final String transfertDir;
        private String serverName;
        private String password;
        private boolean closed = false;
        private State state;
        private ReadingState readingState = ReadingState.WAITING_FOR_REQUEST;
        private Reader<Request> requestReader;

        private Context(SelectionKey key, String login, String transfertDir) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.login = login;
            this.transfertDir = transfertDir;
            this.state = State.PENDING_ANONYMOUS;
        }

        private Context(SelectionKey key, String login, String password, String transfertDir) {
            this(key, login, transfertDir);
            this.state = State.PENDING_PASSWORD;
            this.password = password;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         */
        private void processIn() throws IOException {
            while (bufferIn.position() != 0) {
                if (readingState == ReadingState.WAITING_FOR_REQUEST) {
                    bufferIn.flip();
                    var optionalWatcher = OpCode.getOpCodeFromByte(bufferIn.get());
                    if (optionalWatcher.isPresent()) {
                        requestReader = optionalWatcher.get().getRequestReader();
                        readingState = ReadingState.READING_REQUEST;
                    } else {
                        // Close the connection if it sent a wrong OpCode
                        silentlyClose();
                    }
                    bufferIn.compact();
                }
                // Read the request
                var requestStatus = requestReader.process(bufferIn);
                switch (requestStatus) {
                    case DONE -> {
                        Request request = requestReader.get();
                        requestHandler(request);
                        readingState = ReadingState.WAITING_FOR_REQUEST;
                    }
                    case REFILL -> {
                        return;
                    }
                    case ERROR -> {
                        silentlyClose();
                        logger.severe("Error reading, closing connection");
                        return;
                    }
                }
            }
        }

        private void requestHandler(Request request) throws IOException {
            logger.info(request.toString());
            switch (request) {

                case RequestLoginAccepted requestLoginAccepted -> {
                    if (state != State.CONNECTED) {
                        state = State.CONNECTED;
                        serverName = requestLoginAccepted.serverName().string();
                        logger.info("\t" + "Connection established with server: " + requestLoginAccepted.serverName().string());
                    }
                }

                case RequestLoginRefused requestLoginRefused -> {
                    System.out.println("Connexion refused");
                    silentlyClose();
                }

                case RequestMessagePublic requestMessagePublic -> {
                    // Print Message with login, server, time, and the content
                    var server = requestMessagePublic.serverName().string();
                    var login = requestMessagePublic.login().string();
                    var msg = requestMessagePublic.message().string();
                    var time = LocalDateTime.now();

                    System.out.println(login + "[" + server + "](" + time.getHour() + "h" + time.getMinute() + "): " + msg);
                }

                case RequestMessagePrivate requestMessagePrivate -> {
                    var serverSrc = requestMessagePrivate.serverSrc();
                    var serverDst = requestMessagePrivate.serverDst();
                    var loginSrc = requestMessagePrivate.loginSrc();
                    var loginDst = requestMessagePrivate.loginDst();
                    var msg = requestMessagePrivate.message();
                    var time = LocalDateTime.now();

                    System.out.println("From :" + loginSrc + "[" + serverSrc + "](" + time.getHour() + "h" + time.getMinute() + "): " + msg);
                }

                case RequestMessageFilePrivate requestMessageFilePrivate -> {
                    var filename = requestMessageFilePrivate.filename().string();
                    var file = mapFile.getOrDefault(filename, FileChatFusion.initToReceive(Path.of(transfertDir, filename), requestMessageFilePrivate.nbBlocksMax()));

                    if (file.readUntilWriteAvailable(requestMessageFilePrivate.block(), requestMessageFilePrivate.loginSrc().string(), requestMessageFilePrivate.serverSrc().string())) {
                        mapFile.remove(filename);
                        return;
                    }

                    mapFile.put(filename, file);
                }

                default -> { // Unsupported request, we end the connection with the client
                    logger.severe("Unsupported request:" + request);
                    silentlyClose();
                }
            }
        }

        /**
         * Add a request to the request queue, tries to fill bufferOut and updateInterestOps
         */
        private void queueRequest(Request request) {
            requestQueue.add(request);
            processOut();
            updateInterestOps();
        }

        /**
         * Add a file request to the request queue, tries to fill bufferOut and updateInterestOps
         */
        public void queueFileToSend(Request request) {
            fileRequestQueue.add(request);
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
                var poll = requestQueue.poll();
                assert poll != null;
                var encode = poll.encode();
                bufferOut.put(encode);
            }

            while (!fileRequestQueue.isEmpty()) {
                // inferior to 6_000 byte to prioritise other message than file
                if (bufferOut.remaining() < 6_000 || bufferOut.remaining() < fileRequestQueue.peek().bufferLength()) {
                    return;
                }
                var file = fileRequestQueue.poll();
                assert file != null;
                var encode = file.encode();
                bufferOut.put(encode);
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
            if (!closed && bufferOut.position() != 0) {
                ops |= SelectionKey.OP_WRITE;
            }

            if (ops == 0) {
                silentlyClose();
                return;
            }

            key.interestOps(ops);
        }

        private void silentlyClose() {
            try {
                closed = true;
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
         * @throws IOException
         */
        private void doWrite() throws IOException {
            sc.write(bufferOut.flip());
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        /**
         * Fill the bufferout with the login information to
         * connect to the server
         */
        private void processConnection() {
            // Sending anonymous connection request
            if (state == State.PENDING_ANONYMOUS) {
                queueRequest(RequestFactory.loginAnonymous(login));
            } else { // Sending password connection request
                queueRequest(RequestFactory.loginPassword(login, password));
            }
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) return; // the selector gave a bad hint
            processConnection();
            updateInterestOps();
        }

        public void sendPublicMessage(String login, String message) {
            queueRequest(RequestFactory.publicMessage(serverName, login, message));
        }

        public void sendFilePrivate(String loginSrc, String serverDst, String loginDst, String filename, int nbBlockMax, int blockSize, byte[] block) {
            queueFileToSend(RequestFactory.privateFile(serverName, loginSrc, serverDst, loginDst, filename, nbBlockMax, blockSize, block));
        }
    }
}