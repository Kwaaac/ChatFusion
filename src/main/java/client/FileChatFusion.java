package main.java.client;

import main.java.exceptions.FileChatFusionException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class represent a file being sent or being downloaded by client.
 * As this class can handle both, there is only 2 factory constructor that init a
 * {@link FileChatFusion} either to a client sending file, or a client receiving file.
 *
 * Any use of method for sending file for a client receiving file, {@link FileChatFusionException} will occurs
 *
 * This class allows to handle the sending a file while continuing the flow process of the application.
 * Keeping tract of the sending and keeping track of the downloading
 */
public final class FileChatFusion {
    private final int nbBlocksMax;
    private final ByteBuffer content;
    private final State state;
    private Path filepath;
    private int nbBlockCurrent;

    private FileChatFusion(Path filepath, int nbBlocksMax, ByteBuffer content, State state) {
        this.filepath = filepath;
        this.nbBlocksMax = nbBlocksMax;
        this.content = content;
        this.state = state;
    }

    /**
     * Prepare a {@link FileChatFusion} to send to another client
     *
     * @param filePath path of the file to read the bytes through
     * @return a FileChatFusion, containing the bytes of a file
     * @throws IOException if any I/O exception occurs while reading the file
     */
    public static FileChatFusion initToSend(Path filePath) throws IOException {
        if (!Files.isReadable(filePath)) {
            throw new FileChatFusionException("Wrong file path");
        }

        var content = Files.readAllBytes(filePath);
        var nbBlocksMax = (int) Math.ceil(content.length / 5000d);

        return new FileChatFusion(filePath, nbBlocksMax, ByteBuffer.allocateDirect(content.length).put(content).flip(), State.WRITE);
    }

    /**
     * Prepare a {@link FileChatFusion} to receive an incoming file from a client
     *
     * @param filepath the path of the file
     * @param nbBlocksMax the number of file blocks
     * @return the {@link FileChatFusion} created
     */
    public static FileChatFusion initToReceive(Path filepath, int nbBlocksMax) {
        return new FileChatFusion(filepath, nbBlocksMax, ByteBuffer.allocateDirect(5_000 * nbBlocksMax), State.READ);
    }

    /**
     * Gets the number of file blocks
     * @return number of file blocks
     */
    public int getNbBlocksMax() {
        return nbBlocksMax;
    }

    /**
     * Write the byte contained within a real file.
     *
     * If the file already exist, a mease will duplicate the file by adding `filename (n).exmpl` to the file name
     *
     * @return return the path where the file were written
     * @throws IOException
     */
    private Path writeFile() throws IOException {
        int i = 1;

        // Ensure the file can be created if a file with same name already exist
        while (Files.exists(filepath)) {
            var extensionSplitted = filepath.getFileName().toString().split("\\.");
            var file = extensionSplitted[0];
            var extension = extensionSplitted[1];

            var filename = file + " (" + i++ + ")." + extension;
            filepath = Path.of(filepath.getParent().toString(), filename);
        }

        var block = new byte[content.remaining()];
        content.get(block);
        Files.write(filepath, block);
        return filepath;
    }

    /**
     * Writes partially the contained file stored in the byte array.
     * Uses this method to send a block of the file content
     *
     * @return byte array to send to client
     * @throws FileChatFusionException if the {@link FileChatFusion} is in READ mode
     */
    public byte[] write() {
        if (state == State.READ) {
            throw new FileChatFusionException("Method prohibited");
        }
        var delta = Math.min(content.remaining(), 5_000);
        var oldLimit = content.limit();
        content.limit(delta + content.position());

        var result = new byte[content.remaining()];
        content.get(result);

        content.limit(oldLimit);
        nbBlockCurrent++;
        System.out.println("File " + filepath.getFileName().toString() + " sent as " + nbBlockCurrent + "/" + nbBlocksMax);
        return result;
    }

    /**
     * Read the content of the file, and stores it. If the file is complete, then the file is written in the given client path
     *
     * @param block tthe block containing file data
     * @param loginSrc the sender of the file
     * @param serverNameSrc the server of the sender
     * @return if the file has been download return true, otherwise false
     * @throws IOException If an I/O error occurs
     * @throws FileChatFusionException If the {@link FileChatFusion} is in WRITE mode
     */
    public boolean readUntilWriteAvailable(byte[] block, String loginSrc, String serverNameSrc) throws IOException {
        if (state == State.WRITE) {
            throw new FileChatFusionException("Method prohibited");
        }
        content.put(block);
        nbBlockCurrent++;
        System.out.println("File from " + loginSrc + "[" + serverNameSrc + "]: " + filepath.getFileName().toString() + " download in process " + nbBlockCurrent + "/" + nbBlocksMax);
        if (nbBlockCurrent == nbBlocksMax) {
            Path filePath;
            try {
                content.flip();
                filePath = writeFile();
            } catch (IOException e) {
                System.out.println("File download failed");
                return false;
            }

            System.out.println("File downloaded to: " + filePath.toAbsolutePath());
            return true;
        }

        return false;
    }

    private enum State {
        READ, WRITE
    }
}
