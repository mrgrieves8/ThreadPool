
import java.util.concurrent.*;

import static java.lang.Thread.currentThread;

public class ThreadPool implements Executor {
    private final WaitablePQ<Task<?>> taskQ = new WaitablePQ<>();

    private static final int DEFAULT_NUM_OF_THREADS = Runtime.getRuntime().availableProcessors()*2;
    private static final int MAX_PRIORITY = 10;
    private static final int MIN_PRIORITY = -1;

    private int currentNumOfThreads = 0;
    private volatile boolean isPaused = false;
    private volatile boolean isShut = false;
    private Future<Void> lastPoison;

    public ThreadPool() {
        this(DEFAULT_NUM_OF_THREADS);
    }

    public ThreadPool(int numberOfThreads) {
        if (numberOfThreads <= 0) {
            throw new IllegalArgumentException("numberOfThreads must be more then 0");
        }

        setNumOfThreads(numberOfThreads);
    }

    @Override
    public void execute(Runnable runnable) {
        submit(Executors.callable(runnable), Priority.MEDIUM);
    }

    public Future<?> submit(Runnable runnable) {
        return submit(Executors.callable(runnable), Priority.MEDIUM);
    }

    public Future<?> submit(Runnable runnable, Priority p) {
        return submit(Executors.callable(runnable), p);
    }

    public <T> Future<T> submit(Runnable runnable, Priority p, T value) {
        return submit(Executors.callable(runnable, value), p);
    }

    public <T> Future<T> submit(Callable<T> command) {
        return submit(command, Priority.MEDIUM);
    }

    public <T> Future<T> submit(Callable<T> command, Priority p) {
        if (command == null) {
            throw new NullPointerException();
        }

        if (isShut) {
            throw new RejectedExecutionException("Thread pool is shutdown");
        }

        Task<T> newTask = new Task<>(command, p.ordinal());
        taskQ.enqueue(newTask);

        return newTask.future;
    }

    public void setNumOfThreads(int numOfThreads) {
        if (isShut) {
            throw new RejectedExecutionException();
        }

        if (numOfThreads <= 0) {
            throw new IllegalArgumentException("numberOfThreads must be more then 0");
        }

        if (numOfThreads > currentNumOfThreads) {
            for (int i = 0; i < (numOfThreads - currentNumOfThreads); i++) {
                if (isPaused) {
                    Task<Void> sleepPill = new Task<>(new SleepCall(), MAX_PRIORITY);
                    taskQ.enqueue(sleepPill);
                }
                new Worker().start();
            }
        } else {
            for (int i = 0; i < (currentNumOfThreads - numOfThreads); i++) {
                Task<Void> poison = new Task<>(new PoisonCall(), MAX_PRIORITY);
                poison.killThread = true;
                taskQ.enqueue(poison);
            }
        }

        currentNumOfThreads = numOfThreads;

    }

    public void pause() {
        if (isShut) {
            throw new RejectedExecutionException();
        }

        if (isPaused) {
            return;
        }

        isPaused = true;

        for (int i = 0; i < currentNumOfThreads; i++) {
            Task<Void> sleepPill = new Task<>(new SleepCall(), MAX_PRIORITY);
            taskQ.enqueue(sleepPill);
        }
    }

    public void resume() {
        isPaused = false;

        synchronized (this) {
            notifyAll();
        }
    }

    public void shutdown() {
        isShut = true;

        for (int i = 0; i < currentNumOfThreads - 1; i++) {
            Task<Void> poison = new Task<>(new PoisonCall(), MIN_PRIORITY);
            poison.killThread = true;
            taskQ.enqueue(poison);
        }
        Task<Void> poison = new Task<>(new PoisonCall(), MIN_PRIORITY - 1);
        poison.killThread = true;
        taskQ.enqueue(poison);

        lastPoison = poison.future;
    }

    public void awaitTermination() throws InterruptedException {
        try {
            lastPoison.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            lastPoison.get(timeout, unit);
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private class Task<E> implements Comparable<Task<E>> {
        private final Callable<E> command;
        private final int priority;
        private final Future<E> future;

        private Thread thread;

        private boolean isCancelled = false;
        private boolean isDone = false;
        private boolean killThread = false;

        private E returnVal;
        private ExecutionException thrownException = null;

        public Task(Callable<E> command, int priority) {
            this.command = command;
            this.priority = priority;
            future = new TaskFuture();
        }

        @Override
        public int compareTo(Task<E> task) {
            return task.priority - priority;
        }

        boolean execute() {
            try {
                returnVal = command.call();
                isDone = true;

                synchronized (future) {
                    future.notifyAll();
                }
            } catch (Exception e) {
                thrownException = new ExecutionException(e);
                isDone = true;

                synchronized (future) {
                    future.notifyAll();
                }
            }

            return killThread;
        }

        private class TaskFuture implements Future<E> {

            @Override
            public boolean cancel(boolean b) {
                if (b && thread != null) {
                    thread.interrupt();
                    isCancelled = true;
                    isDone = true;
                    return true;
                }

                boolean ret = taskQ.remove(Task.this);
                if (ret) {
                    isCancelled = true;
                    isDone = true;
                }
                return ret;
            }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public boolean isDone() {
                return isDone;
            }

            @Override
            public E get() throws InterruptedException, ExecutionException {

                if (isCancelled) {
                    throw new CancellationException();
                }


                if (!isDone) {
                    synchronized (future) {
                        while (!isDone) {
                            future.wait();
                            if (thrownException != null) {
                                throw thrownException;
                            }

                        }
                    }
                }



                return returnVal;
            }

            @Override
            public E get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                if (isCancelled) {
                    throw new CancellationException();
                }

                if (!isDone) {
                    synchronized (future) {
                        future.wait(unit.toMillis(timeout));
                    }
                }

                if (thrownException != null) {
                    throw thrownException;
                }

                if (!isDone) {
                    throw new TimeoutException();
                }



                return returnVal;
            }
        }
    }

    private class Worker extends Thread {

        @Override
        public void run() {
            boolean killThread = false;
            while (!killThread) {
                Task<?> task = taskQ.dequeue();
                task.thread = currentThread();
                killThread = task.execute();
            }
        }
    }

    private static class PoisonCall implements Callable<Void> {

        @Override
        public Void call() {
            return null;
        }
    }

    private class SleepCall implements Callable<Void> {

        @Override
        public Void call() throws Exception {
            System.out.println("THIS IS SLEEP in " + currentThread());
            synchronized (ThreadPool.this) {
                while (ThreadPool.this.isPaused) {
                    ThreadPool.this.wait();
                }
            }
            return null;
        }
    }
}


