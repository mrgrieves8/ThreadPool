import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import java.util.concurrent.*;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolTest {
    volatile int count = 0;

    @BeforeEach
    void setUp() {
        count = 0;
    }

    @org.junit.jupiter.api.Test
    void submit() throws ExecutionException, InterruptedException {
        ThreadPool tp = new ThreadPool(6);

        Future<Integer> f1 = tp.submit(new DoSomething());
        Future<Integer> f2 = tp.submit(new DoSomethingElse());

        assertFalse(f1.isDone());
        assertFalse(f2.isDone());

        assertEquals(8, f1.get());
        assertEquals(9, f2.get());

        assertTrue(f1.isDone());
        assertTrue(f2.isDone());

        tp.shutdown();
        tp.awaitTermination();
    }

    @org.junit.jupiter.api.Test
    void setNumOfThreads() throws InterruptedException, ExecutionException {
        ThreadPool tp = new ThreadPool(2);

        tp.submit(new DoSomething(), Priority.HIGH);
        tp.submit(new DoSomethingElse(), Priority.HIGH);
        tp.submit(new DoSomething());
        tp.submit(new DoSomething());
        tp.submit(new DoSomething());
        tp.submit(new IncrementCount(), Priority.HIGH);
        tp.submit(new IncrementCount());
        tp.submit(new TenSeconds("hatul"), Priority.LOW);
        Future<Integer> f8 = tp.submit(new TenSeconds("f8"), Priority.LOW);

        sleep(1000);
        System.out.println("\nSet number of threads to 4");
        tp.setNumOfThreads(4);
        sleep(1000);

        assertTrue(count > 0);

        Future<Integer> f9 = tp.submit(new TenSeconds("f9"), Priority.HIGH);
        tp.submit(new DoSomething());
        tp.submit(new DoSomething());
        tp.submit(new DoSomething());
        tp.submit(new DoSomething());

        sleep(1000);
        System.out.println("\nSet number of threads to 1");
        tp.setNumOfThreads(1);

        f9.get();
        assertFalse(f8.isDone());

        System.out.println("\nSet number of threads to 6");
        tp.setNumOfThreads(6);

        tp.shutdown();

        tp.awaitTermination();
    }

    @org.junit.jupiter.api.Test
    void pause() throws InterruptedException {
        ThreadPool tp = new ThreadPool(2);

        tp.submit(new FourSeconds("Onix"),Priority.HIGH);
        tp.submit(new FourSeconds("Drowzee"),Priority.HIGH);
        tp.submit(new IncrementCount());
        tp.submit(new FourSeconds("Aron"));
        tp.submit(new FourSeconds("Delibird"));

        System.out.println("\npause\n");
        tp.pause();

        sleep(8000);
        assertEquals(0, count);

        System.out.println("\nresume\n");
        tp.resume();

        sleep(1000);
        System.out.println("\npause\n");
        tp.pause();

        sleep(5000);
        System.out.println("\nresume\n");
        tp.resume();

        tp.shutdown();
        tp.awaitTermination();
    }

    @org.junit.jupiter.api.Test
    void pauseWithSetNumOfThreads() throws InterruptedException {
        ThreadPool tp = new ThreadPool(2);

        System.out.println("\npause\n");
        tp.pause();

        tp.submit(new FourSeconds("Onix"),Priority.HIGH);
        tp.submit(new FourSeconds("Drowzee"),Priority.HIGH);
        tp.submit(new IncrementCount(), Priority.LOW);
        tp.submit(new FourSeconds("Aron"));
        tp.submit(new FourSeconds("Delibird"));

        sleep(3000);

        System.out.println("\nresume\n");
        tp.resume();

        sleep(1000);
        System.out.println("\npause\n");
        tp.pause();

        tp.setNumOfThreads(3);

        sleep(5000);
        System.out.println("\nresume\n");
        tp.resume();

        sleep(1000);
        System.out.println("\npause\n");
        tp.pause();

        assertTrue(count > 0);

        tp.resume();

        tp.shutdown();
        tp.awaitTermination();
    }

    @org.junit.jupiter.api.Test
    void cancel() throws InterruptedException {
        ThreadPool tp = new ThreadPool(2);

        Future<?> f4Sec = tp.submit(new FourSeconds("Onix"),Priority.HIGH);
        tp.submit(new FourSeconds("Drowzee"),Priority.HIGH);
        Future<?> fInc = tp.submit(new IncrementCount(), Priority.LOW);
        tp.submit(new FourSeconds("Aron"));
        tp.submit(new FourSeconds("Delibird"));

        sleep(1000);
        assertTrue(fInc.cancel(false));
        assertFalse(f4Sec.cancel(false));

        tp.shutdown();
        tp.awaitTermination();
    }

    @org.junit.jupiter.api.Test
    void shutdown() throws InterruptedException {
        ThreadPool tp = new ThreadPool(2);

        Future<?> f6 = tp.submit(new TenSeconds("f6"));
        System.out.println("\nshutdown");
        tp.shutdown();

        // Await termination and check that tasks complete
        assertFalse(tp.awaitTermination(1, TimeUnit.SECONDS));
        tp.awaitTermination();
        assertTrue(f6.isDone());

        // Test submitting tasks after shutdown (should throw exception)
        assertThrows(RejectedExecutionException.class, () -> tp.submit(new DoSomething()));
    }

    class IncrementCount implements Runnable {
        @Override
        public void run() {
            synchronized (ThreadPoolTest.this) {
                ++count;
            }
            System.out.println(currentThread() + "\nIncremented count: " + count);
        }
    }
}

class DoSomething implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println(currentThread() + " started");
        for (int i = 0; i < 3; i++) {
            sleep(1000);
            System.out.println(currentThread() + " prints something: " + i);
        }
        System.out.println(currentThread() + " done");
        return 8;
    }
}

class DoSomethingElse implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println(currentThread() + " started");
        for (int i = 0; i < 3; i++) {
            sleep(1000);
            System.out.println(currentThread() + " prints something else: " + i);
        }
        System.out.println(currentThread() + " done");
        return 9;
    }
}

class TenSeconds implements Callable<Integer> {
    String name;

    TenSeconds(String name) {
        this.name = name;
    }
    @Override
    public Integer call() throws Exception {
        System.out.println(currentThread() + " started");
        for (int i = 0; i < 10; i++) {
            sleep(1000);
            System.out.println(name + " seconds passed: " + i);
        }
        System.out.println(currentThread() + " done");
        return 9;
    }
}


class FourSeconds implements Callable<Integer> {
    String name;

    FourSeconds(String name) {
        this.name = name;
    }
    @Override
    public Integer call() throws Exception {
        System.out.println(currentThread() + " started");
        for (int i = 0; i < 4; i++) {
            sleep(1000);
            System.out.println(name + " seconds passed: " + i);
        }
        System.out.println(currentThread() + " done");
        return 9;
    }
}

class Request implements Callable<Map.Entry<String, String>> {
    String request;
    Map.Entry<String, String> entry;

    @Override
    public Map.Entry<String, String> call() throws Exception {
        return null;
    }
}
