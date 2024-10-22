import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class WaitablePQ <E> {
    PriorityQueue<E> pq;
    Semaphore sem = new Semaphore(0);

    public WaitablePQ() {
        this(null);
    }

    public WaitablePQ(Comparator<E> comparator) {
        pq = new PriorityQueue<>(comparator);
    }

    public void enqueue(E e) {
        synchronized (this) {
            pq.add(e);
        }

        sem.release();
    }

    public E dequeue() {
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        synchronized (this) {
            return pq.poll();
        }
    }

    public E dequeue(long timeout, TimeUnit unit) {
        return dequeue();
    }

    public boolean remove(Object o) {
        boolean ret;

        synchronized (this) {
            ret = pq.remove(o);
        }

        if (ret) {
            try {
                sem.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return ret;
    }

    public synchronized E peek() {
        return pq.peek();
    }

    public synchronized int size() {
        return pq.size();
    }

    public synchronized boolean isEmpty() {
        return pq.isEmpty();
    }

}
