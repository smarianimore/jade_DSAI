import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ProposeInitiator;

/**
 * Initiator in a FIPA-Propose negotiation about how to spend three weekends.
 * Run with: jade.Boot -agents "husband:BattleOfSexesHusband(wife),wife:BattleOfSexesWife(husband)"
 */
public class BattleOfSexesHusband extends Agent {
    private static final long STEP_DELAY_MS = 5000; // slows the negotiation down so it can be followed
    private static final String[] OFFERS = {
            BattleOfSexes.MOVIE,
            BattleOfSexes.MOVIE + "," + BattleOfSexes.FRIENDS,
            BattleOfSexes.MOVIE + "," + BattleOfSexes.FRIENDS + "," + BattleOfSexes.FRIENDS
    };

    private String wife;
    private int offerIndex;
    private BattleOfSexesGui gui;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        wife = args != null && args.length > 0 ? args[0].toString() : "wife";
        gui = BattleOfSexesGui.open("Husband - " + getLocalName());
        gui.setStatus("Prefers movies, negotiating with " + wife);
        System.out.println(getLocalName() + " prefers movies and will negotiate with " + wife + ".");
        proposeNextSchedule();
    }

    @Override
    protected void takeDown() {
        if (gui != null) {
            gui.close();
        }
    }

    private void proposeNextSchedule() {
        if (offerIndex >= OFFERS.length) {
            gui.setStatus("No agreement was reached");
            System.out.println(getLocalName() + ": no agreement was reached.");
            return;
        }
        addBehaviour(new WakerBehaviour(this, STEP_DELAY_MS) {
            @Override
            protected void onWake() {
                sendProposal();
            }
        });
    }

    private void sendProposal() {
        final int round = offerIndex + 1;
        final String schedule = OFFERS[offerIndex++];
        ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
        proposal.addReceiver(new AID(wife, AID.ISLOCALNAME));
        proposal.setProtocol(FIPANames.InteractionProtocol.FIPA_PROPOSE);
        proposal.setContent(schedule);
        System.out.println(getLocalName() + " proposes: " + schedule);
        gui.log(round, "sent", "PROPOSE", schedule);
        gui.setStatus("Round " + round + ": proposed " + schedule);

        addBehaviour(new ProposeInitiator(this, proposal) {
            @Override
            protected void handleAcceptProposal(ACLMessage accept) {
                gui.log(round, "received", "ACCEPT_PROPOSAL", schedule);
                gui.setStatus("Deal! " + schedule + " (husband "
                        + BattleOfSexes.husbandUtility(schedule) + ", wife "
                        + BattleOfSexes.wifeUtility(schedule) + ")");
                System.out.println(accept.getSender().getLocalName() + " accepts: " + schedule);
                System.out.println("Agreement: " + schedule
                        + " | husband utility=" + BattleOfSexes.husbandUtility(schedule)
                        + ", wife utility=" + BattleOfSexes.wifeUtility(schedule));
            }

            @Override
            protected void handleRejectProposal(ACLMessage reject) {
                gui.log(round, "received", "REJECT_PROPOSAL", reject.getContent());
                gui.setStatus("Round " + round + ": rejected, wife counters with " + reject.getContent());
                System.out.println(reject.getSender().getLocalName()
                        + " counters with: " + reject.getContent());
                proposeNextSchedule();
            }
        });
    }
}
