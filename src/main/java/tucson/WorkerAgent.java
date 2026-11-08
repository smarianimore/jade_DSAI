package tucson;

import alice.tuple.logic.LogicTuple;
import alice.tuplecentre.respect.api.TupleCentreId;
import alice.tuplecentre.tucson.api.TucsonOperation;
import alice.tuplecentre.tucson.api.acc.OrdinarySyncACC;

import java.util.concurrent.Callable;

final class WorkerAgent implements Callable<Void> {
    private final String name;
    private final String host;
    private final int port;
    private final TupleCentreId centre;

    WorkerAgent(String name, String host, int port, TupleCentreId centre) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.centre = centre;
    }

    @Override
    public Void call() throws Exception {
        OrdinarySyncACC acc = TucsonSupport.connect(name, host, port);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // TuCSoN binds variables in a successful template; use a fresh one each time.
                TucsonOperation operation = acc.inp(centre, LogicTuple.parse("work(Id,Number)"), null);
                if (!operation.isResultSuccess()) {
                    Thread.sleep(50);
                    continue;
                }
                LogicTuple work = operation.getLogicTupleResult();
                int id = work.getArg(0).intValue();
                if (id == -1) {
                    System.out.println(name + " stopped");
                    return null;
                }
                int number = work.getArg(1).intValue();
                long answer = factorial(number);
                TucsonSupport.out(acc, centre, "result(" + id + "," + number + "," + answer + "," + name + ")");
                System.out.println(name + " completed job " + id);
            }
            throw new InterruptedException(name + " interrupted");
        } finally {
            acc.exit();
        }
    }

    static long factorial(int number) {
        if (number < 0 || number > 12) {
            throw new IllegalArgumentException("Factorial input must be between 0 and 12");
        }
        long result = 1;
        for (int i = 2; i <= number; i++) {
            result *= i;
        }
        return result;
    }
}
