package hic.update;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.Desktop;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class UpdateController {
    private final JFrame window;
    private boolean busy;

    private UpdateController(JFrame window) {
        this.window = window;
    }

    public static void install(JFrame window) {
        UpdateController controller = new UpdateController(window);
        JMenuBar menuBar = window.getJMenuBar();
        if (menuBar == null) {
            menuBar = new JMenuBar();
            window.setJMenuBar(menuBar);
        }
        JMenu help = new JMenu("Help");
        JMenuItem check = new JMenuItem("Check for Updates…");
        check.addActionListener(event -> controller.check(true));
        help.add(check);
        menuBar.add(help);
        window.revalidate();
        Timer timer = new Timer(5000, event -> controller.check(false));
        timer.setRepeats(false);
        timer.start();
    }

    private void check(boolean manual) {
        if (busy) {
            return;
        }
        busy = true;
        new SwingWorker<GitHubUpdateService.Release, Void>() {
            private GitHubUpdateService service;

            @Override
            protected GitHubUpdateService.Release doInBackground() throws Exception {
                service = new GitHubUpdateService();
                return service.check();
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    GitHubUpdateService.Release release = get();
                    if (release == null) {
                        if (manual) {
                            JOptionPane.showMessageDialog(window, "No newer compatible release is available.\nInstalled version: "
                                    + service.version(), "HIC Studio Updates", JOptionPane.INFORMATION_MESSAGE);
                        }
                        return;
                    }
                    JTextArea notes = new JTextArea(release.notes(), 10, 60);
                    notes.setEditable(false);
                    notes.setLineWrap(true);
                    notes.setWrapStyleWord(true);
                    notes.setCaretPosition(0);
                    int choice = JOptionPane.showOptionDialog(window,
                            new Object[]{"HIC Studio " + release.version() + " is available (installed: " + service.version() + ").",
                                    new JScrollPane(notes), "Would you like to download the update?"},
                            "Update Available", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                            null, new String[]{"Download Update", "Later"}, "Later");
                    if (choice == 0) {
                        download(service, release);
                    }
                } catch (Exception exception) {
                    if (manual) {
                        showError(exception);
                    } else {
                        System.err.println("Update check: " + message(exception));
                    }
                }
            }
        }.execute();
    }

    private void download(GitHubUpdateService service, GitHubUpdateService.Release release) {
        busy = true;
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setStringPainted(true);
        progress.setString("Downloading HIC Studio " + release.version() + "…");
        progress.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JDialog downloadDialog = new JDialog(window, "Downloading Update", false);
        downloadDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        downloadDialog.add(progress);
        downloadDialog.setSize(420, 90);
        downloadDialog.setLocationRelativeTo(window);
        downloadDialog.setVisible(true);
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return service.download(release);
            }

            @Override
            protected void done() {
                busy = false;
                downloadDialog.dispose();
                try {
                    Path installer = get();
                    boolean mac = release.fileName().endsWith(".dmg");
                    String instructions = mac
                            ? "HIC Studio will close and open the disk image.\nDrag HIC Studio into Applications and choose Replace, then reopen the app."
                            : "HIC Studio will close and start the Windows installer.\nFollow the installer prompts, then reopen the app.";
                    int choice = JOptionPane.showConfirmDialog(window,
                            "The update has been downloaded and verified.\nFinish any current work before installing.\n\n"
                                    + instructions + "\n\nInstall now?\nDownloaded installer: " + installer,
                            "Install Update", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.YES_OPTION) {
                        launchInstaller(installer);
                        window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                    }
                } catch (Exception exception) {
                    showError(exception);
                }
            }
        }.execute();
    }

    private void showError(Exception exception) {
        JOptionPane.showMessageDialog(window, "Unable to update HIC Studio.\n" + message(exception),
                "HIC Studio Updates", JOptionPane.WARNING_MESSAGE);
    }

    private static void launchInstaller(Path installer) throws IOException {
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                String windowsDirectory = System.getenv("SystemRoot");
                if (windowsDirectory == null || windowsDirectory.isBlank()) {
                    throw new IOException("The Windows system directory could not be located.");
                }
                Path executable = Path.of(windowsDirectory, "System32", "msiexec.exe");
                new ProcessBuilder(executable.toString(), "/i", installer.toAbsolutePath().toString(),
                        "/norestart").start();
            } else {
                Desktop.getDesktop().open(installer.toFile());
            }
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            throw new IOException("The installer could not be started: " + exception.getMessage()
                    + "\n\nYour downloaded installer is saved at:\n" + installer
                    + "\nClose HIC Studio and run this installer manually.");
        }
    }

    private static String message(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
