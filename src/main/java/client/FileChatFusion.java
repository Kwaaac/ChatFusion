package main.java.client;

import main.java.exceptions.FileChatFusionException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

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
     * Will put the content {@link ByteBuffer} to read-mode as there is no more data to put into it
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    public static FileChatFusion initToSend(Path filePath) throws IOException {
        if (!Files.isReadable(filePath)) {
            throw new FileChatFusionException("Wrong file path");
        }

        var content = Files.readAllBytes(filePath);
        var nbBlocksMax = (int) Math.ceil(content.length / 5000d);

        return new FileChatFusion(filePath, nbBlocksMax, ByteBuffer.allocateDirect(content.length).put(content).flip(), State.WRITE);
    }

    public static FileChatFusion initToReceive(Path filepath, int nbBlocksMax) {
        return new FileChatFusion(filepath, nbBlocksMax, ByteBuffer.allocateDirect(5_000 * nbBlocksMax), State.READ);
    }

    public int getNbBlocksMax() {
        return nbBlocksMax;
    }

    /**
     * Write the file into an actual file
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
     * content buffer is in readMode
     *
     * @return byte array to send to client
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
