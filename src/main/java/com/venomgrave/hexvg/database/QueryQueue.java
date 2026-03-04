package com.venomgrave.hexvg.database;

import com.venomgrave.hexvg.util.DBLogger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Kolejka zapytań SQL z ograniczeniem przepustowości.
 * Zapobiega przeciążeniu bazy przy dużej liczbie graczy.
 *
 * Priorytety:
 *   HIGH   - zapytania wywołane przez gracza (JOIN, komendy)
 *   NORMAL - standardowe zapytania Skript
 *   LOW    - operacje w tle (backup, statystyki)
 */
public class QueryQueue {

    public enum Priority { HIGH, NORMAL, LOW }

    private final PriorityBlockingQueue<QueuedTask> queue;
    private final ExecutorService worker;
    private final AtomicLong taskCounter = new AtomicLong(0);

    private final int maxQueueSize;
    private boolean enabled;

    public QueryQueue(boolean enabled, int maxQueueSize) {
        this.enabled = enabled;
        this.maxQueueSize = maxQueueSize > 0 ? maxQueueSize : 500;

        this.queue = new PriorityBlockingQueue<>(64, (a, b) -> {
            int cmp = a.priority.compareTo(b.priority);
            return cmp != 0 ? cmp : Long.compare(a.id, b.id); // FIFO w obrębie priorytetu
        });

        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HexVG-QueryWorker");
            t.setDaemon(true);
            return t;
        });

        // Jeden wątek procesuje kolejkę
        worker.submit(this::processLoop);
    }

    /**
     * Dodaje zadanie do kolejki.
     * Jeśli kolejka jest pełna lub disabled — wykonuje natychmiast na osobnym wątku.
     */
    public void enqueue(Runnable task, Priority priority) {
        if (!enabled || queue.size() >= maxQueueSize) {
            // Fallback: wykonaj bezpośrednio
            CompletableFuture.runAsync(task);
            return;
        }
        queue.offer(new QueuedTask(task, priority, taskCounter.incrementAndGet()));
    }

    /**
     * Wygodna metoda z domyślnym priorytetem NORMAL.
     */
    public void enqueue(Runnable task) {
        enqueue(task, Priority.NORMAL);
    }

    private void processLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                QueuedTask task = queue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    try {
                        task.runnable.run();
                    } catch (Exception e) {
                        DBLogger.severe("QueryQueue task error: " + e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void shutdown() {
        worker.shutdownNow();
        queue.clear();
    }

    public int queueSize() {
        return queue.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static class QueuedTask {
        final Runnable runnable;
        final Priority priority;
        final long id;

        QueuedTask(Runnable runnable, Priority priority, long id) {
            this.runnable = runnable;
            this.priority = priority;
            this.id = id;
        }
    }
}