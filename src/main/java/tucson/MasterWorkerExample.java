package tucson;

import alice.tuple.logic.LogicTuple;
import alice.tuplecentre.respect.api.TupleCentreId;
import alice.tuplecentre.tucson.api.TucsonOperation;
import alice.tuplecentre.tucson.api.acc.OrdinarySyncACC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MasterWorkerExample {
    private static final long TIMEOUT_SECONDS = 60;

    private MasterWorkerExample() {
    }

    public static void main(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        boolean inspect = false;
        for (String arg : args) {
            if ("--inspect".equals(arg) && !inspect) {
                inspect = true;
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown or duplicate option: " + arg);
            } else {
                positional.add(arg);
            }
        }
        if (positional.size() > 3) {
            throw new IllegalArgumentException("Usage: MasterWorkerExample [--inspect] [host [port [workers]]]");
        }
        String host = positional.size() > 0 ? positional.get(0) : "localhost";
        int port = positional.size() > 1 ? Integer.parseInt(positional.get(1)) : 20504;
        int workers = positional.size() > 2 ? Integer.parseInt(positional.get(2)) : 3;
        if (host.isEmpty() || port < 1 || port > 65535 || workers < 1 || workers > 16) {
            throw new IllegalArgumentException("Host must be nonempty, port 1-65535 and workers 1-16");
        }
        run(host, port, workers, new int[]{0, 3, 5, 7, 10, 12}, inspect);
    }

    static void run(String host, int port, int workerCount, int[] numbers) throws Exception {
        run(host, port, workerCount, numbers, false);
    }

    static void run(String host, int port, int workerCount, int[] numbers, boolean inspect) throws Exception {
        if (workerCount < 1 || workerCount > 16) {
            throw new IllegalArgumentException("Workers must be between 1 and 16");
        }
        for (int number : numbers) {
            if (number < 0 || number > 12) {
                throw new IllegalArgumentException("Factorial input must be between 0 and 12");
            }
        }
        String runId = UUID.randomUUID().toString().replace("-", "");
        TupleCentreId centre = new TupleCentreId("mw" + runId, host, Integer.toString(port));
        OrdinarySyncACC master = TucsonSupport.connect("master" + runId, host, port);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<Void>> workers = new ArrayList<>();
        InspectorSession inspector = null;
        boolean completed = false;
        try {
            if (inspect) {
                System.out.println("Inspecting tuple centre " + centre.getLocalName() + "@" + host + ":" + port);
                inspector = InspectorSession.start(centre, host, port);
                inspector.awaitStart();
            }
            for (int i = 0; i < workerCount; i++) {
                String name = "worker" + i + "r" + runId;
                workers.add(executor.submit(new WorkerAgent(name, host, port, centre)));
            }
            for (int i = 0; i < numbers.length; i++) {
                TucsonSupport.out(master, centre, "work(" + i + "," + numbers[i] + ")");
                System.out.println("master submitted job " + i + " for " + numbers[i] + "!");
            }

            Set<Integer> received = new HashSet<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (received.size() < numbers.length) {
                for (Future<Void> worker : workers) {
                    if (worker.isDone()) {
                        worker.get();
                        throw new IllegalStateException("A worker stopped before all results arrived");
                    }
                }
                if (!inspect && System.nanoTime() >= deadline) {
                    throw new TimeoutException("Timed out waiting for worker results");
                }
                // Matching binds the template variables, so it cannot be reused.
                TucsonOperation operation = master.inp(centre,
                        LogicTuple.parse("result(Id,Number,Answer,Worker)"), null);
                if (!operation.isResultSuccess()) {
                    Thread.sleep(50);
                    continue;
                }
                LogicTuple result = operation.getLogicTupleResult();
                int id = result.getArg(0).intValue();
                int number = result.getArg(1).intValue();
                if (id < 0 || id >= numbers.length || number != numbers[id] || !received.add(id)) {
                    throw new IllegalStateException("Unexpected result: " + result);
                }
                System.out.println("master received job " + id + ": " + number + "! = "
                        + result.getArg(2) + " from " + result.getArg(3));
            }
            for (int i = 0; i < workerCount; i++) {
                TucsonSupport.out(master, centre, "work(-1,0)");
            }
            for (Future<Void> worker : workers) {
                if (inspect) {
                    worker.get();
                } else {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        throw new TimeoutException("Timed out waiting for workers to stop");
                    }
                    worker.get(remaining, TimeUnit.NANOSECONDS);
                }
            }
            System.out.println("master collected " + received.size() + " results");
            completed = true;
        } finally {
            executor.shutdownNow();
            master.exit();
            if (!completed && inspector != null) {
                inspector.stop();
            }
        }
        if (inspector != null) {
            try {
                inspector.awaitClose();
            } finally {
                inspector.stop();
            }
        }
    }
}
