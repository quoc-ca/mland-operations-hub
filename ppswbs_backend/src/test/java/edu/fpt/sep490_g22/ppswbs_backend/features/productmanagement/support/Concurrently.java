package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs actions on separate threads that start together, and returns each result or failure in order. */
public final class Concurrently {

    public record Outcome<T>(T value, Throwable failure) {
        public boolean succeeded() {
            return failure == null;
        }
    }

    private Concurrently() {
    }

    @SafeVarargs
    public static <T> List<Outcome<T>> run(Callable<T>... actions) {
        ExecutorService pool = Executors.newFixedThreadPool(actions.length);
        CyclicBarrier start = new CyclicBarrier(actions.length);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> action : actions) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(60, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                }
            }
            return outcomes;
        } catch (Exception e) {
            throw new IllegalStateException("Concurrent run did not finish", e);
        } finally {
            pool.shutdownNow();
        }
    }
}
