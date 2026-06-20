package com.smartbillingpro.util;

import javax.swing.SwingUtilities;
import java.io.File;

/**
 * Watches the shared SQLite database on a background thread and triggers a UI
 * refresh whenever it changes.
 *
 * <p>This is what makes every open window update in near real time - including
 * the separate Admin and Store apps when they are pointed at the same database
 * file (a shared folder on a LAN, or a synced cloud folder). As soon as a sale,
 * product, purchase or account is written, the watcher notices the file change
 * and refreshes the screen that is currently showing.</p>
 */
public final class LiveSync {

    private final File dbFile;
    private final Runnable onChange;
    private final long intervalMs;
    private Thread thread;
    private volatile boolean running;
    private long lastSignature;

    public LiveSync(File dbFile, Runnable onChange) {
        this(dbFile, onChange, 1500L);
    }

    public LiveSync(File dbFile, Runnable onChange, long intervalMs) {
        this.dbFile = dbFile;
        this.onChange = onChange;
        this.intervalMs = intervalMs;
        this.lastSignature = signature();
    }

    /**
     * A cheap fingerprint of the database state. SQLite in WAL mode writes to a
     * separate {@code -wal} file, so we combine the size + modified-time of the
     * main file and its WAL/SHM siblings to catch every write.
     */
    private long signature() {
        long sig = 0L;
        sig += stamp(dbFile);
        sig += stamp(new File(dbFile.getAbsolutePath() + "-wal"));
        sig += stamp(new File(dbFile.getAbsolutePath() + "-shm"));
        return sig;
    }

    private long stamp(File f) {
        return (f != null && f.exists()) ? f.lastModified() + f.length() : 0L;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "smartbilling-livesync");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                return;
            }
            long current = signature();
            if (current != lastSignature) {
                lastSignature = current;
                if (running) {
                    SwingUtilities.invokeLater(() -> {
                        if (running) {
                            onChange.run();
                        }
                    });
                }
            }
        }
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
