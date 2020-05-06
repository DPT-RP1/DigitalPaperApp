package net.sony.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncUtils {

    public static void waitForFinished(final AtomicBoolean finished, final int timeoutSeconds) throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (finished.get()) executor.shutdownNow();
        }, 0, 1, TimeUnit.SECONDS);
        executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
    }

}
