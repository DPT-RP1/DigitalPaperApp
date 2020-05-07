package net.sony.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncUtils {

    public static void waitForFinished(final AtomicBoolean finished, final int timeoutSeconds) throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final ScheduledFuture<?>[] future = new ScheduledFuture<?>[1];
        future[0] = executor.scheduleAtFixedRate(() -> {
            if (finished.get()) {
                future[0].cancel(true);
                executor.shutdownNow();
            }
        }, 0, 1, TimeUnit.SECONDS);
        executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
    }

}
