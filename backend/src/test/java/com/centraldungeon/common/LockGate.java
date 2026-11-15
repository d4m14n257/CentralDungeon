package com.centraldungeon.common;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Makes a race between transactions reproducible instead of lucky.
 *
 * <p>A test thread opens its own transaction, takes the same row lock the code under test is about to
 * ask for, and keeps it. The contenders are then started; each one does whatever it reads before its
 * own locking statement and queues behind the gate. Once {@link #awaitWaiters} sees that many
 * transactions waiting on a lock, every contender has already taken its pre-lock snapshot, so
 * {@link #open()} lets them through in exactly the interleaving that exposes a read made before the
 * lock. A {@code CountDownLatch} alone only makes that interleaving possible; this makes it certain.
 *
 * <p>Use it in a try-with-resources block: closing it opens the gate and waits for the holder thread,
 * so a failing assertion never leaves a transaction holding a lock for the rest of the class.
 */
public final class LockGate implements AutoCloseable {

    private static final Duration HOLDER_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(20);

    private final CountDownLatch locked = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final Thread holder;
    private volatile Throwable failure;

    private LockGate(PlatformTransactionManager transactionManager, Runnable takeLock) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        holder = Thread.ofPlatform().name("lock-gate").start(() -> {
            try {
                transaction.executeWithoutResult(status -> {
                    takeLock.run();
                    locked.countDown();
                    awaitQuietly(released);
                });
            } catch (Throwable e) {
                failure = e;
                locked.countDown();
            }
        });
    }

    /**
     * Opens a transaction on a thread of its own, runs the locking statement in it and keeps the
     * transaction open until the gate is opened.
     *
     * @param transactionManager the application's transaction manager
     * @param takeLock           the locking read to hold, e.g. a repository's {@code findByIdForUpdate}
     * @return the gate, already holding the lock
     * @throws IllegalStateException if the lock could not be taken in time or the statement failed
     */
    public static LockGate hold(PlatformTransactionManager transactionManager, Runnable takeLock) {
        LockGate gate = new LockGate(transactionManager, takeLock);
        if (!awaitQuietly(gate.locked) || gate.failure != null) {
            gate.released.countDown();
            throw new IllegalStateException("The gate could not take its lock", gate.failure);
        }
        return gate;
    }

    /**
     * A template that can see every transaction's lock state, for {@link #awaitWaiters}.
     *
     * <p>{@code performance_schema.data_lock_waits} is not readable by the container's application
     * user; Testcontainers gives {@code root} the same password, so the monitor connects as
     * {@code root}. It only ever reads that view.
     *
     * @param mysql the test class's container
     * @return a template connected as {@code root} to the same database
     */
    public static JdbcTemplate monitor(MySQLContainer mysql) {
        return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
    }

    /**
     * Blocks until at least {@code count} transactions are waiting on a lock, which is the moment every
     * contender has finished its pre-lock reads.
     *
     * @param jdbc    a template allowed to read every transaction's state, from {@link #monitor}
     * @param count   how many contenders must be queued
     * @param timeout how long to wait before failing the test
     * @throws IllegalStateException if fewer were queued when the timeout ran out
     */
    public void awaitWaiters(JdbcTemplate jdbc, int count, Duration timeout) {
        awaitUntil(jdbc, waiting -> waiting >= count, timeout);
    }

    /**
     * Blocks until the number of transactions waiting on a lock satisfies {@code settled}. For races
     * where not every contender ends up waiting - two inserts into the same gap, for instance, deadlock
     * and one of them is rolled back at once instead of queueing.
     *
     * @param jdbc    a template allowed to read every transaction's state, from {@link #monitor}
     * @param settled given how many transactions are waiting, whether the contenders are where the test
     *                needs them
     * @param timeout how long to wait before failing the test
     * @throws IllegalStateException if the condition never held before the timeout ran out
     */
    public void awaitUntil(JdbcTemplate jdbc, IntPredicate settled, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        int waiting = 0;
        while (System.nanoTime() < deadline) {
            // data_lock_waits and not innodb_trx: a transaction whose first write is the one waiting
            // has only done consistent reads, and innodb_trx does not list it at all yet.
            Integer current = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits",
                    Integer.class);
            waiting = current == null ? 0 : current;
            if (settled.test(waiting)) {
                return;
            }
            sleep(POLL_INTERVAL);
        }
        throw new IllegalStateException("The contenders never settled behind the gate; transactions waiting: " + waiting);
    }

    /** Commits the holder's transaction, releasing the lock to whoever queued first. */
    public void open() {
        released.countDown();
    }

    /**
     * Opens the gate if it is still closed and waits for the holder to finish.
     *
     * @throws IllegalStateException if the holder did not finish in time
     */
    @Override
    public void close() {
        open();
        try {
            if (!holder.join(HOLDER_TIMEOUT)) {
                throw new IllegalStateException("The gate's transaction did not finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing the gate", e);
        }
    }

    private static boolean awaitQuietly(CountDownLatch latch) {
        try {
            return latch.await(HOLDER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the gate's waiters", e);
        }
    }
}
