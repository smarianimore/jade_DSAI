import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Vector;

/**
 * Load balancer of the ContractNet example. Periodically generates a task (work units + deadline),
 * sends a CFP to all the ContractNetWorker agents found in the DF, and assigns the task to the
 * bidder promising the earliest completion; the other bidders are rejected.
 * Arguments (optional): number of tasks (default 10), interval between tasks in ms (default 1000).
 * Run with: jade.Boot -agents "w1:ContractNetWorker(100);w2:ContractNetWorker(200);lb:ContractNetLoadBalancer(10)"
 */
public class ContractNetLoadBalancer extends Agent {

    private static final long REPLY_TIMEOUT_MS = 2000;

    private int tasks = 10;
    private long intervalMs = 1000;
    private int inFlight = 0;
    private final Random random = new Random();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            tasks = Integer.parseInt(args[0].toString());
        }
        if (args != null && args.length > 1) {
            intervalMs = Long.parseLong(args[1].toString());
        }

        ContractNetGui.addNode(getLocalName(), true);
        updateGui();
        addBehaviour(new TickerBehaviour(this, intervalMs) {
            private int generated = 0;

            @Override
            protected void onTick() {
                if (generated >= tasks) {
                    stop();
                    return;
                }
                generated++;
                int work = 50 + random.nextInt(250);           // work units
                long deadline = 1000 + random.nextInt(2500);   // ms, relative to now
                distribute("task" + generated, work, deadline);
            }
        });
    }

    private void distribute(final String taskId, int work, long deadlineMs) {
        List<AID> workers = findWorkers();
        if (workers.isEmpty()) {
            log(taskId + ": no workers available");
            return;
        }
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        for (AID w : workers) {
            cfp.addReceiver(w);
        }
        cfp.setContent(taskId + ";" + work + ";" + deadlineMs);
        cfp.setReplyByDate(new Date(System.currentTimeMillis() + REPLY_TIMEOUT_MS + 4L * ContractNetGui.stepDelayMs()));
        log("CFP " + taskId + ": work " + work + ", deadline " + deadlineMs + " ms, to " + workers.size() + " workers");

        for (AID w : workers) {
            ContractNetGui.message(getLocalName(), w.getLocalName(), "CFP", cfp.getContent());
        }
        addBehaviour(new ContractNetInitiator(this, cfp) {
            @Override
            protected void handlePropose(ACLMessage propose, Vector acceptances) {
                log(taskId + ": " + propose.getSender().getLocalName() + " bids " + propose.getContent() + " ms");
            }

            @Override
            protected void handleRefuse(ACLMessage refuse) {
                log(taskId + ": " + refuse.getSender().getLocalName() + " refuses");
            }

            @Override
            protected void handleAllResponses(Vector responses, Vector acceptances) {
                ContractNetGui.pause(); // let the last PROPOSE/REFUSE animation arrive before deciding
                ACLMessage bestProposal = null;
                long bestTime = Long.MAX_VALUE;
                for (Object o : responses) {
                    ACLMessage msg = (ACLMessage) o;
                    if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        long time = Long.parseLong(msg.getContent());
                        if (time < bestTime) {
                            bestTime = time;
                            bestProposal = msg;
                        }
                    }
                }
                if (bestProposal == null) {
                    log(taskId + ": NO BID within the deadline, task dropped");
                    return;
                }
                for (Object o : responses) {
                    ACLMessage msg = (ACLMessage) o;
                    if (msg.getPerformative() != ACLMessage.PROPOSE) {
                        continue;
                    }
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(msg == bestProposal ? ACLMessage.ACCEPT_PROPOSAL : ACLMessage.REJECT_PROPOSAL);
                    ContractNetGui.message(getLocalName(), msg.getSender().getLocalName(),
                            msg == bestProposal ? "ACCEPT" : "REJECT", taskId);
                    acceptances.add(reply);
                }
                inFlight++;
                updateGui();
                log(taskId + ": assigned to " + bestProposal.getSender().getLocalName() + " (" + bestTime + " ms)");
            }

            @Override
            protected void handleInform(ACLMessage inform) {
                inFlight--;
                updateGui();
                log(taskId + ": completed by " + inform.getSender().getLocalName());
            }

            @Override
            protected void handleFailure(ACLMessage failure) {
                inFlight--;
                updateGui();
                log(taskId + ": FAILED at " + failure.getSender().getLocalName());
            }
        });
    }

    private List<AID> findWorkers() {
        List<AID> result = new ArrayList<>();
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(ContractNetWorker.SERVICE_TYPE);
        template.addServices(sd);
        try {
            for (DFAgentDescription d : DFService.search(this, template)) {
                result.add(d.getName());
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void updateGui() {
        ContractNetGui.setNodeState(getLocalName(), 0, inFlight + " tasks assigned");
    }

    @Override
    protected void takeDown() {
        ContractNetGui.removeNode(getLocalName());
    }

    private void log(String msg) {
        System.out.println("[" + getLocalName() + "] " + msg);
    }
}
