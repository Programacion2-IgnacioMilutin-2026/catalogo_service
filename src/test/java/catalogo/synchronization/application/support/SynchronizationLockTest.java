package catalogo.synchronization.application.support;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

// El candado de las sincronizaciones.
class SynchronizationLockTest {

    private final SynchronizationLock lock = new SynchronizationLock();

    @Test
    void tryAcquireDoesNotWaitWhileAnotherThreadHoldsTheLock() throws InterruptedException {
        Holder holder = Holder.start(lock);

        assertThat(lock.tryAcquire()).isFalse();

        holder.releaseAndJoin();
        assertThat(lock.tryAcquire()).isTrue();
        lock.release();
    }

    @Test
    void aWaitingThreadTakesTheLockWhenTheHolderReleasesIt() throws InterruptedException {
        Holder holder = Holder.start(lock);
        CountDownLatch acquired = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean();
        Thread waiting = Thread.ofVirtual().start(() -> {
            result.set(lock.acquire());
            acquired.countDown();
            lock.release();
        });

        assertThat(acquired.await(300, TimeUnit.MILLISECONDS)).isFalse();

        holder.releaseAndJoin();
        assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
        waiting.join();
        assertThat(result).isTrue();
    }

    @Test
    void anInterruptedWaitEndsWithoutTheLockAndKeepsTheInterruption() throws InterruptedException {
        Holder holder = Holder.start(lock);
        AtomicBoolean result = new AtomicBoolean(true);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread waiting = Thread.ofVirtual().start(() -> {
            result.set(lock.acquire());
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        Thread.sleep(200);

        waiting.interrupt();
        waiting.join(5000);

        assertThat(waiting.isAlive()).isFalse();
        assertThat(result).isFalse();
        assertThat(interrupted).isTrue();
        holder.releaseAndJoin();
        assertThat(lock.tryAcquire()).isTrue();
        lock.release();
    }

    @Test
    void theHolderEntersAgainWithoutWaiting() {
        assertThat(lock.tryAcquire()).isTrue();

        assertThat(lock.acquire()).isTrue();

        lock.release();
        lock.release();
    }

    // Un hilo que toma el candado y lo suelta cuando el test lo pide.
    private record Holder(Thread thread, CountDownLatch release) {

        static Holder start(SynchronizationLock lock) throws InterruptedException {
            CountDownLatch taken = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread thread = Thread.ofVirtual().start(() -> {
                lock.tryAcquire();
                taken.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.release();
                }
            });
            assertThat(taken.await(5, TimeUnit.SECONDS)).isTrue();
            return new Holder(thread, release);
        }

        void releaseAndJoin() throws InterruptedException {
            release.countDown();
            thread.join(5000);
        }
    }
}
