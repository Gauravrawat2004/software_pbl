package filebackup;

import java.nio.file.*;
import java.util.function.Consumer; // Add this import

public class SyncManager {
    private Path syncDir;
    private Consumer<String> logger;

    public SyncManager(String syncPath, Consumer<String> logger) throws Exception {
        this.syncDir = Paths.get(syncPath);
        this.logger = logger;
        if (!Files.exists(syncDir)) {
            Files.createDirectories(syncDir);
            logger.accept("Created sync directory: " + syncPath);
        }
        if (!Files.isDirectory(syncDir)) {
            throw new Exception("Sync path is not a directory: " + syncPath);
        }
        if (!Files.isWritable(syncDir)) {
            throw new Exception("Sync path is not writable: " + syncPath);
        }
    }

    public void syncFolders(Path sourceDir) {
        try {
            logger.accept("Starting sync: " + sourceDir + " <-> " + syncDir);
            // Source to Sync
            for (Path srcFile : Files.newDirectoryStream(sourceDir)) {
                Path syncFile = syncDir.resolve(srcFile.getFileName());
                if (!Files.exists(syncFile)) {
                    Files.copy(srcFile, syncFile);
                    logger.accept("Synced: " + srcFile + " -> " + syncFile);
                } else if (Files.getLastModifiedTime(srcFile).toMillis() >
                        Files.getLastModifiedTime(syncFile).toMillis()) {
                    Files.copy(srcFile, syncFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.accept("Updated sync: " + syncFile);
                }
            }

            // Sync to Source (bidirectional)
            for (Path syncFile : Files.newDirectoryStream(syncDir)) {
                Path srcFile = sourceDir.resolve(syncFile.getFileName());
                if (!Files.exists(srcFile)) {
                    Files.copy(syncFile, srcFile);
                    logger.accept("Synced: " + syncFile + " -> " + srcFile);
                } else if (Files.getLastModifiedTime(syncFile).toMillis() >
                        Files.getLastModifiedTime(srcFile).toMillis()) {
                    Files.copy(syncFile, srcFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.accept("Updated source: " + srcFile);
                }
            }
            logger.accept("Sync completed");
        } catch (Exception e) {
            logger.accept("Sync error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}