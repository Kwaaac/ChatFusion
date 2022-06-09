package main.java.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    public static Path getPathToDownload(){
        return Path.of(System.getProperty("user.dir"), "src", "main", "resources", "download");
    }

    public static Path getPathToDownloadWithFileName(String fileName){
        return Path.of(System.getProperty("user.dir"), "src", "main", "resources", "downloads", fileName);
    }
}
