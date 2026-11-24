package com.smartbillingpro.app;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guarantees only one copy of the app uses the data folder at a time, so two
 * windows can never write conflicting sales into the same database.
 */
public final class InstanceLock {

    private RandomAccessFile file;
    private FileChannel channel;
    private FileLock lock;

    public boolean acquire(Path lockFile) {
        try {
            Files.createDirectories(lockFile.getParent());
            file = new RandomAccessFile(lockFile.toFile(), "rw");
            channel = file.getChannel();
            lock = channel.tryLock();
            return lock != null;
        } catch (IOException | OverlappingFileLockException e) {
            return false;
        }
    }

    public void release() {
        try {
            if (lock != null) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
            if (file != null) {
                file.close();
            }
        } catch (IOException ignored) {
            // best effort on shutdown
        }
    }
}
