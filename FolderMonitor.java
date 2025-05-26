package filebackup;

import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FolderMonitor {
    private Map<String, Long> fileTimes = new HashMap<>();
    private Path sourceDir;
    private String fileFilter;
    private Consumer<String> logger;

    public FolderMonitor(String sourcePath, String filter, Consumer<String> logger) throws Exception {
        this.sourceDir = Paths.get(sourcePath);
        this.fileFilter = filter.isEmpty() ? null : filter.toLowerCase();
        this.logger = logger;
        if (!Files.isDirectory(sourceDir)) {
            throw new Exception("Source path is not a directory: " + sourcePath);
        }
        if (!Files.isReadable(sourceDir)) {
            throw new Exception("Source path is not readable: " + sourcePath);
        }
        logger.accept("Initialized polling monitor for: " + sourcePath);
    }

    public void monitor(Consumer<Map<String, Path>> onChange) {
        logger.accept("Starting polling loop");
        try {
            while (true) {
                Map<String, Path> changedFiles = detectChanges();
                if (!changedFiles.isEmpty()) {
                    onChange.accept(changedFiles);
                }
                Thread.sleep(5000); // Poll every 5 seconds
            }
        } catch (InterruptedException e) {
            logger.accept("Polling interrupted, stopping");
        } catch (Exception e) {
            logger.accept("Polling error: " + e.getMessage());
            throw new RuntimeException("Polling failed", e);
        }
    }

    private Map<String, Path> detectChanges() {
        Map<String, Path> changedFiles = new HashMap<>();
        Map<String, Long> currentFiles = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (fileFilter != null && !fileName.toLowerCase().endsWith(fileFilter)) {
                    logger.accept("Skipped file due to filter: " + fileName);
                    continue;
                }
                long lastModified = Files.getLastModifiedTime(file).toMillis();
                currentFiles.put(fileName, lastModified);
                if (!fileTimes.containsKey(fileName)) {
                    logger.accept("New file: " + fileName);
                    changedFiles.put(fileName, file);
                } else if (!fileTimes.get(fileName).equals(lastModified)) {
                    logger.accept("Modified file: " + fileName);
                    changedFiles.put(fileName, file);
                }
            }
        } catch (Exception e) {
            logger.accept("Error scanning folder: " + e.getMessage());
        }
        fileTimes.clear();
        fileTimes.putAll(currentFiles);
        return changedFiles;
    }

    public void stop() {
        logger.accept("Polling monitor stopped");
    }
}