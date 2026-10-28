import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SSContractNetResponder;
import jade.proto.SSResponderDispatcher;

/**
 * Processing agent of the ContractNet example: bids to complete a task within its deadline.
 * CFP content: "taskId;workUnits;deadlineMs" (deadline relative to the CFP).
 * The bid is the estimated completion time (ms from now), given the speed (work units/s) and the
 * work already committed. Tasks whose estimated completion exceeds the deadline are refused.
 * Run with: jade.Boot -agents "w1:ContractNetWorker(100)"
 */
public class ContractNetWorker extends Agent {

    static final String SERVICE_TYPE = "task-processing";

    private double speed = 100; // work units per second
    private int pendingTasks = 0;
    private long busyUntil = 0; // epoch ms at which the already accepted tasks are done

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            speed = Double.parseDouble(args[0].toString());
        }

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_TYPE);
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
            doDelete();
            return;
        }

        ContractNetGui.addNode(getLocalName(), false);
        updateGui();
        log("ready, speed " + speed + " units/s");
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));
        // One responder per incoming CFP, so that several negotiations can run concurrently
        addBehaviour(new SSResponderDispatcher(this, template) {
            @Override
            protected Behaviour createResponder(ACLMessage cfpMsg) {
                return new Responder(myAgent, cfpMsg);
            }
        });
    }

    private class Responder extends SSContractNetResponder {
        Responder(Agent a, ACLMessage cfp) {
            super(a, cfp);
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
            ContractNetGui.pause(); // let the CFP animation arrive before the worker answers
            String[] fields = cfp.getContent().split(";");
            if (fields.length != 3) {
                throw new NotUnderstoodException("expected taskId;work;deadlineMs");
            }
            long completion = estimateCompletionMs(Double.parseDouble(fields[1]));
            long deadline = Long.parseLong(fields[2]);
            if (completion > deadline) {
                log("refuse " + fields[0] + ": would need " + completion + " ms > deadline " + deadline + " ms");
                ContractNetGui.message(getLocalName(), cfp.getSender().getLocalName(), "REFUSE", fields[0]);
                throw new RefuseException("deadline-unreachable");
            }
            log("propose " + fields[0] + ": " + completion + " ms (deadline " + deadline + " ms)");
            ACLMessage propose = cfp.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.valueOf(completion));
            ContractNetGui.message(getLocalName(), cfp.getSender().getLocalName(), "PROPOSE", fields[0] + " " + completion + " ms");
            return propose;
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            String[] fields = cfp.getContent().split(";");
            final String taskId = fields[0];
            long now = System.currentTimeMillis();
            long start = Math.max(now, busyUntil);
            busyUntil = start + Math.round(Double.parseDouble(fields[1]) / speed * 1000);
            final long delay = busyUntil - now;
            log("start " + taskId + ", done in " + delay + " ms");
            pendingTasks++;
            updateGui();

            // Reply only once the work is done, so the initiator gets the INFORM at completion
            final ACLMessage inform = accept.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            inform.setContent(taskId + " done");
            myAgent.addBehaviour(new WakerBehaviour(myAgent, delay) {
                @Override
                protected void onWake() {
                    log("finished " + taskId);
                    pendingTasks--;
                    updateGui();
                    ContractNetGui.message(getLocalName(), accept.getSender().getLocalName(), "INFORM", inform.getContent());
                    myAgent.send(inform);
                }
            });
            return null; // the INFORM is sent by the waker; the session stays open until then
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            log("proposal for " + cfp.getContent().split(";")[0] + " rejected");
        }
    }

    private long estimateCompletionMs(double work) {
        long now = System.currentTimeMillis();
        return Math.max(now, busyUntil) - now + Math.round(work / speed * 1000);
    }

    private void updateGui() {
        ContractNetGui.setNodeState(getLocalName(), pendingTasks, speed + " u/s, " + pendingTasks + " queued");
    }

    @Override
    protected void takeDown() {
        ContractNetGui.removeNode(getLocalName());
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {
        }
    }

    private void log(String msg) {
        System.out.println("[" + getLocalName() + "] " + msg);
    }
}
