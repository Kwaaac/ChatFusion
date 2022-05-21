package main.java.client;

import main.java.OpCode;
import main.java.reader.*;

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
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

public class ClientChat {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    static private final int BUFFER_SIZE = 10_000;
    static private final Logger logger = Logger.getLogger(ClientChat.class.getName());
    private final SocketChannel sc;
    private final SendThreadSafe sendThreadSafe = new SendThreadSafe();
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final String login;
    private final Thread console;
    private String password;
    private Context uniqueContext;

    public ClientChat(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        if (login.getBytes(UTF8).length > 30) {
            throw new IllegalArgumentException("Login length should be less than 30 bytes");
        }
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
    }

    public ClientChat(String login, String password, InetSocketAddress serverAddress) throws IOException {
        this(login, serverAddress);
        if (password.getBytes(UTF8).length > 30) {
            throw new IllegalArgumentException("Password length should be less than 30 bytes");
        }
        this.password = password;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 3 && args.length != 4) {
            usage();
            return;
        }
        if (args.length == 4) {
            new ClientChat(args[2], args[3], new InetSocketAddress(args[0], Integer.parseInt(args[1]))).launch();
            return;
        }

        new ClientChat(args[2], new InetSocketAddress(args[0], Integer.parseInt(args[1]))).launch();
    }

    private static void usage() {
        System.out.println("""
                Usages :
                Login anonymous: java ClientChat [hostname] [post] [login]
                                
                Login with password: java ClientChat [hostname] [post] [login] [password]
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
        uniqueContext = password == null ? new Context(key, login) : new Context(key, login, password);
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

    /**
     * Processes the command from the BlockingQueue
     */
    private void processCommand() {
        var msg = sendThreadSafe.processCommand();
        if (msg == null) {
            return;
        }

        uniqueContext.queueMessage(new Message(login, msg));
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
        private final ArrayDeque<Message> queue = new ArrayDeque<>();
        private final MessageReader messageReader = new MessageReader();

        private final StringReader stringReader = new StringReader();

        private final IntReader intReader = new IntReader();

        private String serverName;
        private String password;
        private boolean closed = false;
        private State state;

        private OpCode watcher = OpCode.IDLE;

        private Context(SelectionKey key, String login) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.login = login;
            this.state = State.PENDING_ANONYMOUS;
        }

        private Context(SelectionKey key, String login, String password) {
            this(key, login);
            this.state = State.PENDING_PASSWORD;
        }

        /**
         * handle the connection answer, set the context to connect if the connection is
         * accepted, return refill for a refill if the server name is not complete
         * or return an error if there the connection is refused or the string reader fail
         *
         * @return DONE if connected to server,
         * REFILL if servername is not complete,
         * ERROR if connection refused or reading failed
         */
        private Reader.ProcessStatus handleLogin() {
            // If not a problem, because the client is not connected
            if (watcher != OpCode.LOGIN_ACCEPTED) {
                var result = bufferIn.getInt();
                if (result == 3) {
                    System.out.println("Connection refused");
                    return Reader.ProcessStatus.ERROR;
                }
                watcher = OpCode.LOGIN_ACCEPTED;
            }

            var status = stringReader.process(bufferIn);
            if (status == Reader.ProcessStatus.DONE) {
                serverName = stringReader.get();
                this.state = State.CONNECTED;
                stringReader.reset();
            }

            return status;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         */
        private void processIn() {
            while (!closed && bufferIn.hasRemaining()) {
                if (state != State.CONNECTED) {
                    handleLogin();
                }

                var process = messageReader.process(bufferIn);
                switch (process) {
                    case DONE -> {
                        System.out.println(messageReader.get());
                        messageReader.reset();
                    }
                    case REFILL -> {
                        return;
                    }
                    case ERROR -> silentlyClose();
                }
            }
            bufferIn.compact();
            messageReader.reset();
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         */
        private void queueMessage(Message msg) {
            queue.add(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            while (!queue.isEmpty()) {
                if (bufferOut.remaining() >= queue.peek().length(UTF8)) {
                    var encode = queue.poll().encode(UTF8);
                    bufferOut.put(encode);
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
            sc.write(bufferOut.flip());
            bufferOut.compact();
            updateInterestOps();
        }

        /**
         * Fill the bufferout with the login information to
         * connect to the server
         */
        private void processConnection() {
            // Sending anonymous connection request
            if (state == State.PENDING_ANONYMOUS) {
                bufferOut.putInt(OpCode.LOGIN_ANONYMOUS.getOpCode());
                //login
                bufferOut.putInt(login.getBytes(UTF8).length);
                bufferOut.put(UTF8.encode(login));

            } else { // Sending password connection request
                bufferOut.putInt(OpCode.LOGIN_PASSWORD.getOpCode());
                //login
                bufferOut.putInt(login.getBytes(UTF8).length);
                bufferOut.put(UTF8.encode(login));
                //pwd
                bufferOut.putInt(password.getBytes(UTF8).length);
                bufferOut.put(UTF8.encode(password));
            }
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) return; // the selector gave a bad hint
            processConnection();
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }
}