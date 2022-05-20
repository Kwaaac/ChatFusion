package server;

import reader.Message;
import reader.MessageReader;
import reader.Reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ServerChatStringWithConsoleAndTimout {
    private static final Charset UTF8 = UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(ServerChatStringWithConsoleAndTimout.class.getName());
    private static final long TIMEOUT = 60_000;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Thread console;
    private final StateController stateController = new StateController();

    public ServerChatStringWithConsoleAndTimout(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerChatStringWithConsoleAndTimout(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumBetter port");
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
            logger.log(Level.INFO, "Connection closed with client due to IOException", e);
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
     * Add a message to all connected clients queue
     *
     * @param msg The message to broadcast to every clients
     */
    private void broadcast(Message msg) {
        selector.keys().stream().filter(selectionKey -> !selectionKey.isAcceptable()).map(key -> (Context) key.attachment()).forEach(context -> context.queueMessage(msg));
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
        private final MessageReader messageReader = new MessageReader();
        private final ArrayDeque<Message> queue = new ArrayDeque<>();
        private final ServerChatStringWithConsoleAndTimout server; // we could also have Context as an instance class,
        private boolean activeSinceLastTimeoutCheck = true;
        // which would naturally give access to ServerChatInt.this
        private boolean closed = false;

        private Context(ServerChatStringWithConsoleAndTimout server, SelectionKey key) {
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
        private void processIn() {
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
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         *
         * @param msg
         */
        public void queueMessage(Message msg) {
            queue.add(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            while (!queue.isEmpty()) {
                var message = queue.peek();
                if (bufferOut.remaining() >= message.length(UTF8) + Integer.BYTES * 2) {
                    message = queue.pop();
                    bufferOut.putInt(message.login().getBytes(UTF_8).length).put(UTF8.encode(message.login())).putInt(message.msg().getBytes(UTF_8).length).put(UTF8.encode(message.msg()));
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

            if (!closed && (bufferOut.position() != 0 || !queue.isEmpty())) {
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

            activeSinceLastTimeoutCheck = false;

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