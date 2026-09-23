package com.example.poultryscanfinal;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared background executor for Room work, plus a main-thread handler for
 * posting results back. Room must never be touched on the UI thread.
 */
public final class AppExecutors {

    private static final ExecutorService DISK_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static ExecutorService diskIO() {
        return DISK_IO;
    }

    public static Handler mainThread() {
        return MAIN;
    }
}
