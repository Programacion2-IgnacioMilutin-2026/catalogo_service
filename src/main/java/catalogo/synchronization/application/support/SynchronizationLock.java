package catalogo.synchronization.application.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

// Candado para tener una sincronizacion a la vez.
@Component
public class SynchronizationLock {

    private final ReentrantLock lock = new ReentrantLock();

    // No espera: devuelve false si otra sincronización está en curso.
    public boolean tryAcquire() {
        return lock.tryLock();
    }

    public void release() {
        lock.unlock();
    }
}
