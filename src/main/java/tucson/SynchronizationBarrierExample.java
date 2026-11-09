package tucson;

import alice.tuple.logic.LogicTuple;
import alice.tuplecentre.respect.api.TupleCentreId;
import alice.tuplecentre.tucson.api.TucsonOperation;
import alice.tuplecentre.tucson.api.acc.OrdinarySyncACC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class SynchronizationBarrierExample {
    private SynchronizationBarrierExample() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: SynchronizationBarrierExample [host [port [agents]]]");
        }
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 20504;
        int agentCount = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        if (host.isEmpty() || port < 1 || port > 65535 || agentCount < 1 || agentCount > 16) {
            throw new IllegalArgumentException("Host must be nonempty, port 1-65535 and agents 1-16");
        }

        String runId = UUID.randomUUID().toString().replace("-", "");
        TupleCentreId centre = new TupleCentreId("barrier" + runId, host, Integer.toString(port));
        OrdinarySyncACC coordinator = TucsonSupport.connect("coordinator" + runId, host, port);
        ExecutorService executor = Executors.newFixedThreadPool(agentCount);
        List<Future<Void>> agents = new ArrayList<>();
        try {
            TucsonSupport.out(coordinator, centre, "rendezvous(" + agentCount + ")");
            System.out.println("Coordinator initialized rendezvous(" + agentCount + ")");

            for (int i = 0; i < agentCount; i++) {
                String name = "participant" + i + "r" + runId;
                agents.add(executor.submit(new BarrierAgent(name, host, port, centre, i)));
            }
            for (Future<Void> agent : agents) {
                agent.get();
            }
            System.out.println("All agents passed the rendezvous");
        } finally {
            executor.shutdownNow();
            coordinator.exit();
        }
    }

    private static final class BarrierAgent implements Callable<Void> {
        private final String name;
        private final String host;
        private final int port;
        private final TupleCentreId centre;
        private final int id;

        private BarrierAgent(String name, String host, int port, TupleCentreId centre, int id) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.centre = centre;
            this.id = id;
        }

        @Override
        public Void call() throws Exception {
            OrdinarySyncACC acc = TucsonSupport.connect(name, host, port);
            try {
                System.out.println(name + " is doing work before the rendezvous");
                Thread.sleep((id % 3) * 300L);
                System.out.println(name + " reached the rendezvous");

                TucsonOperation operation = acc.in(centre, LogicTuple.parse("rendezvous(Remaining)"), null);
                if (!operation.isResultSuccess()) {
                    throw new IllegalStateException(name + " could not read the rendezvous count");
                }
                int remaining = operation.getLogicTupleResult().getArg(0).intValue();
                if (remaining < 1) {
                    throw new IllegalStateException("Invalid rendezvous count: " + remaining);
                }
                TucsonSupport.out(acc, centre, "rendezvous(" + (remaining - 1) + ")");
                System.out.println(name + " decremented the count to " + (remaining - 1));

                operation = acc.rd(centre, LogicTuple.parse("rendezvous(0)"), null);
                if (!operation.isResultSuccess()) {
                    throw new IllegalStateException(name + " could not read the completed rendezvous");
                }
                System.out.println(name + " passed the rendezvous and resumed work");
                return null;
            } finally {
                acc.exit();
            }
        }
    }
}
