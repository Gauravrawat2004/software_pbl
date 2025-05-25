package filebackup;

import com.formdev.flatlaf.FlatLightLaf; // For modern look
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileBackupGUI {
    private JFrame frame;
    private JTextField sourceField, backupField, syncField;
    private JTextArea logArea;
    private JComboBox<String> fileFilterCombo;
    private JButton startButton, stopButton;
    private JProgressBar progressBar;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private FolderMonitor monitor;
    private BackupManager backupManager;
    private SyncManager syncManager;
    private Thread progressThread;

    public FileBackupGUI() {
        // Set modern FlatLaf look
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            log("Failed to set FlatLaf: " + e.getMessage());
        }

        // Set up the main frame
        frame = new JFrame("File Backup and Synchronization Tool");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600); // Larger window for table
        frame.setLayout(new BorderLayout());

        // North panel: Folder selection and file filter
        JPanel inputPanel = new JPanel(new GridLayout(4, 3, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Source folder with drag-and-drop
        inputPanel.add(new JLabel("Source Folder:"));
        sourceField = new JTextField(20);
        sourceField.setEditable(false);
        enableDragAndDrop(sourceField);
        inputPanel.add(sourceField);
        JButton sourceBrowse = new JButton("Browse");
        sourceBrowse.addActionListener(e -> chooseFolder(sourceField));
        inputPanel.add(sourceBrowse);

        // Backup folder
        inputPanel.add(new JLabel("Backup Folder:"));
        backupField = new JTextField(20);
        backupField.setEditable(false);
        enableDragAndDrop(backupField);
        inputPanel.add(backupField);
        JButton backupBrowse = new JButton("Browse");
        backupBrowse.addActionListener(e -> chooseFolder(backupField));
        inputPanel.add(backupBrowse);

        // Sync folder (optional)
        inputPanel.add(new JLabel("Sync Folder (Optional):"));
        syncField = new JTextField(20);
        syncField.setEditable(false);
        enableDragAndDrop(syncField);
        inputPanel.add(syncField);
        JButton syncBrowse = new JButton("Browse");
        syncBrowse.addActionListener(e -> chooseFolder(syncField));
        inputPanel.add(syncBrowse);

        // File filter
        inputPanel.add(new JLabel("File Filter:"));
        String[] filters = {"All Files", ".txt", ".jpg", ".pdf"};
        fileFilterCombo = new JComboBox<>(filters);
        inputPanel.add(fileFilterCombo);
        inputPanel.add(new JLabel());

        // Center panel: Log area and file table
        JPanel centerPanel = new JPanel(new GridLayout(2, 1));
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Status Log"));
        centerPanel.add(logScrollPane);

        // File table
        tableModel = new DefaultTableModel(new Object[]{"File Name", "Status"}, 0);
        fileTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(fileTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Monitored Files"));
        centerPanel.add(tableScrollPane);

        // South panel: Controls and progress bar
        JPanel controlPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        progressBar.setForeground(Color.BLUE); // Animated color
        controlPanel.add(buttonPanel, BorderLayout.NORTH);
        controlPanel.add(progressBar, BorderLayout.SOUTH);

        // Add panels to frame
        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.SOUTH);

        // Button actions
        startButton.addActionListener(e -> startMonitoring());
        stopButton.addActionListener(e -> stopMonitoring());

        // Center the frame
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Enable drag-and-drop for folder fields
    private void enableDragAndDrop(JTextField field) {
        field.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<java.io.File> droppedFiles = (java.util.List<java.io.File>)
                            evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        java.io.File file = droppedFiles.get(0);
                        if (file.isDirectory()) {
                            field.setText(file.getAbsolutePath());
                            log("Dropped folder: " + file.getAbsolutePath());
                        } else {
                            log("Please drop a folder, not a file");
                        }
                    }
                } catch (Exception e) {
                    log("Drag-and-drop error: " + e.getMessage());
                }
            }
        });
    }

    // Choose folder using JFileChooser
    private void chooseFolder(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
            log("Selected folder: " + field.getText());
        }
    }

    // Start monitoring, backup, and sync
    private void startMonitoring() {
        log("Start button clicked");
        if (isRunning.get()) {
            log("Tool is already running!");
            return;
        }

        String sourcePath = sourceField.getText();
        String backupPath = backupField.getText();
        String syncPath = syncField.getText();
        String filter = (String) fileFilterCombo.getSelectedItem();
        String extension = filter.equals("All Files") ? "" : filter;

        if (sourcePath.isEmpty() || backupPath.isEmpty()) {
            log("Please select source and backup folders!");
            return;
        }

        try {
            log("Initializing monitor for: " + sourcePath);
            monitor = new FolderMonitor(sourcePath, extension, this::log);
            backupManager = new BackupManager(backupPath, this::log);
            syncManager = syncPath.isEmpty() ? null : new SyncManager(syncPath, this::log);

            isRunning.set(true);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            progressBar.setString("Monitoring...");
            progressBar.setForeground(Color.GREEN);

            // Start progress bar animation
            progressThread = new Thread(() -> {
                while (isRunning.get()) {
                    for (int i = 0; i <= 100 && isRunning.get(); i += 5) {
                        progressBar.setValue(i);
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                progressBar.setValue(0);
                progressBar.setString("Idle");
                progressBar.setForeground(Color.BLUE);
            });
            progressThread.start();

            // Start monitoring thread
            Thread monitorThread = new Thread(() -> {
                try {
                    monitor.monitor(changedFiles -> {
                        log("Processing " + changedFiles.size() + " changed files");
                        for (Map.Entry<String, Path> entry : changedFiles.entrySet()) {
                            String fileName = entry.getKey();
                            backupManager.backupFile(entry.getValue(), fileName);
                            updateFileTable(fileName, "Backed up");
                            JOptionPane.showMessageDialog(frame, "Backed up: " + fileName, "Success", JOptionPane.INFORMATION_MESSAGE);
                        }
                        if (syncManager != null) {
                            syncManager.syncFolders(Paths.get(sourcePath));
                            log("Synchronized folders: " + sourcePath + " <-> " + syncPath);
                        }
                    });
                } catch (Exception e) {
                    log("Monitor thread failed: " + e.getMessage());
                    progressBar.setForeground(Color.RED);
                }
            });
            monitorThread.start();

            log("Started monitoring with filter: " + (extension.isEmpty() ? "All Files" : extension));
            updateFileTableFromFolder(sourcePath); // Initial file list
        } catch (Exception e) {
            log("Error starting tool: " + e.getMessage());
            stopMonitoring();
        }
    }

    // Stop monitoring
    private void stopMonitoring() {
        log("Stop button clicked");
        if (!isRunning.get()) {
            log("Tool is not running!");
            return;
        }

        isRunning.set(false);
        if (monitor != null) {
            monitor.stop();
        }
        if (progressThread != null) {
            progressThread.interrupt();
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        progressBar.setString("Idle");
        progressBar.setForeground(Color.BLUE);
        log("Tool stopped.");
    }

    // Update file table with initial folder contents
    private void updateFileTableFromFolder(String folderPath) {
        tableModel.setRowCount(0); // Clear table
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(folderPath))) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    tableModel.addRow(new Object[]{file.getFileName().toString(), "Not backed up"});
                }
            }
        } catch (Exception e) {
            log("Error loading files: " + e.getMessage());
        }
    }

    // Update file table status
    private void updateFileTable(String fileName, String status) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(fileName)) {
                tableModel.setValueAt(status, i, 1);
                return;
            }
        }
        tableModel.addRow(new Object[]{fileName, status});
    }

    // Append message to log area
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            System.out.println(message);
        });
    }

    public static void main(String[] args) {
        // Run GUI on Event Dispatch Thread
        SwingUtilities.invokeLater(FileBackupGUI::new);
    }
}