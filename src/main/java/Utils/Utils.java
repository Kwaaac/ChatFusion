package main.java.Utils;

import java.nio.file.Path;

public class Utils {

    /**
     * Creates a {@link Path} to the "download" directory
     * @return the {@link Path} created
     */
    public static Path getPathToDownload(){
        return Path.of(System.getProperty("user.dir"), "src", "main", "resources", "download");
    }

    /**
     * Creates a {@link Path} to the file to download
     * @param fileName the name of the file to download
     * @return the {@link Path} created
     */
    public static Path getPathToDownloadWithFileName(String fileName){
        return Path.of(System.getProperty("user.dir"), "src", "main", "resources", "downloads", fileName);
    }
}
