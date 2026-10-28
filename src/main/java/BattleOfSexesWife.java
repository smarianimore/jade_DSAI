import jade.core.Agent;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ProposeResponder;

/** Responder in the battle-of-the-sexes FIPA-Propose negotiation. */
public class BattleOfSexesWife extends Agent {
    private String husband;
    private int round;
    private BattleOfSexesGui gui;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        husband = args != null && args.length > 0 ? args[0].toString() : "husband";
        gui = BattleOfSexesGui.open("Wife - " + getLocalName());
        gui.setStatus("Prefers friends, waiting for " + husband + "'s proposal");
        MessageTemplate template = ProposeResponder.createMessageTemplate(
                FIPANames.InteractionProtocol.FIPA_PROPOSE);
        addBehaviour(new ProposeResponder(this, template) {
            @Override
            protected ACLMessage prepareResponse(ACLMessage proposal) {
                String schedule = proposal.getContent();
                round++;
                gui.log(round, "received", "PROPOSE", schedule);
                System.out.println(getLocalName() + " receives from "
                        + proposal.getSender().getLocalName() + ": " + schedule);

                ACLMessage response = proposal.createReply();
                if (!proposal.getSender().getLocalName().equals(husband)
                        || !BattleOfSexes.isValidSchedule(schedule)) {
                    response.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    response.setContent("Expected a schedule of MOVIE and FRIENDS choices.");
                    gui.log(round, "sent", "NOT_UNDERSTOOD", schedule);
                } else if (BattleOfSexes.husbandUtility(schedule) >= 4
                        && BattleOfSexes.wifeUtility(schedule) >= 4) {
                    response.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    response.setContent(schedule);
                    gui.log(round, "sent", "ACCEPT_PROPOSAL", schedule);
                    gui.setStatus("Deal! " + schedule + " (husband "
                            + BattleOfSexes.husbandUtility(schedule) + ", wife "
                            + BattleOfSexes.wifeUtility(schedule) + ")");
                    System.out.println(getLocalName() + " accepts the schedule.");
                } else {
                    response.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    response.setContent(counterOffer(schedule));
                    gui.log(round, "sent", "REJECT_PROPOSAL", response.getContent());
                    gui.setStatus("Round " + round + ": rejected, countering with " + response.getContent());
                    System.out.println(getLocalName() + " rejects and counters with: "
                            + response.getContent());
                }
                return response;
            }
        });
        System.out.println(getLocalName() + " prefers time with friends and is waiting for "
                + husband + "'s proposal.");
    }

    @Override
    protected void takeDown() {
        if (gui != null) {
            gui.close();
        }
    }

    private String counterOffer(String schedule) {
        int weekends = schedule.split(",").length;
        return weekends == 1
                ? BattleOfSexes.FRIENDS
                : BattleOfSexes.FRIENDS + "," + BattleOfSexes.MOVIE;
    }
}
