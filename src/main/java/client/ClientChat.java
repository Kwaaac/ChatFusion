package main.java.client;

import main.java.reader.Message;
import main.java.reader.MessageReader;

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
    private Context uniqueContext;

    public ClientChat(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 3) {
            usage();
            return;
        }

        new ClientChat(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : main.java.client.ClientChat login hostname port");
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
        uniqueContext = new Context(key);
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
        if(msg == null){
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
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Message> queue = new ArrayDeque<>();
        private final MessageReader messageReader = new MessageReader();
        private boolean closed = false;

        private Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         */
        private void processIn() {
            while (!closed && bufferIn.hasRemaining()) {
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
         *
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

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) return; // the selector gave a bad hint
            key.interestOps(SelectionKey.OP_READ);
        }
    }
}