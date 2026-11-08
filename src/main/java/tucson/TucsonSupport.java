package tucson;

import alice.tuple.logic.LogicTuple;
import alice.tuplecentre.respect.api.TupleCentreId;
import alice.tuplecentre.tucson.api.TucsonMetaACC;
import alice.tuplecentre.tucson.api.TucsonOperation;
import alice.tuplecentre.tucson.api.acc.NegotiationACC;
import alice.tuplecentre.tucson.api.acc.OrdinarySyncACC;

final class TucsonSupport {
    private TucsonSupport() {
    }

    static OrdinarySyncACC connect(String agent, String host, int port) throws Exception {
        NegotiationACC negotiation = TucsonMetaACC.getNegotiationContext(agent, host, port);
        if (negotiation == null) {
            throw new IllegalStateException("Cannot create coordination context for " + agent);
        }
        OrdinarySyncACC acc = negotiation.playDefaultRole();
        if (acc == null) {
            throw new IllegalStateException("Default role unavailable for " + agent);
        }
        return acc;
    }

    static void out(OrdinarySyncACC acc, TupleCentreId centre, String tuple) throws Exception {
        TucsonOperation operation = acc.out(centre, LogicTuple.parse(tuple), null);
        if (!operation.isResultSuccess()) {
            throw new IllegalStateException("Could not write tuple " + tuple);
        }
    }
}
