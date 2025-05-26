package filebackup;

import java.nio.file.*;
import java.util.function.Consumer; // Ensure this import

public class BackupManager {
    private Path backupDir;
    private Consumer<String> logger;

    public BackupManager(String backupPath, Consumer<String> logger) throws Exception {
        this.backupDir = Paths.get(backupPath);
        this.logger = logger;
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
            logger.accept("Created backup directory: " + backupPath);
        }
        if (!Files.isDirectory(backupDir)) {
            throw new Exception("Backup path is not a directory: " + backupPath);
        }
        if (!Files.isWritable(backupDir)) {
            throw new Exception("Backup path is not writable: " + backupPath);
        }
    }

    public void backupFile(Path sourceFile, String fileName) {
        try {
            Path backupFile = backupDir.resolve(fileName);
            logger.accept("Attempting to back up: " + sourceFile + " -> " + backupFile);
            Files.copy(sourceFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            logger.accept("Backed up: " + sourceFile + " -> " + backupFile);
        } catch (Exception e) {
            logger.accept("Error backing up " + fileName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}