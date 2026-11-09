# JADE Agents and TuCSoN Coordination

Examples for the Data Science and Artificial Intelligence course of the Digital
Automation Engineering master's degree at the University of Modena and Reggio
Emilia (Italy). The repository demonstrates two complementary technologies:
autonomous agents and FIPA interaction protocols implemented with JADE, and
coordination through shared tuple centres implemented with TuCSoN. The TuCSoN
examples are standalone Java programs; they do not run on the JADE platform.

Course home page: [https://moodle.unimore.it/course/view.php?id=22084](https://moodle.unimore.it/course/view.php?id=22084)

## Dependencies

- **Java** (JDK 8 or later; Swing/AWT support is needed for the JADE example GUIs)
- **Maven** 3.x
- **JADE 4.5.0**: the distribution is already bundled in `deps/JADE-all-4.5.0/`. The Maven build references `deps/JADE-all-4.5.0/jade/lib/jade.jar` as a `system`-scoped dependency, so no download is needed.
- **TuCSoN 0.2.9**: used by the standalone coordination examples. Maven downloads the [TuCSoN](https://github.com/TuCSoN-Coord/TuCSoN) middleware and its dependencies from Maven Central.

## Compilation

From the project root:

```
mvn install
```

This produces `target/jade-playground-1.0-SNAPSHOT.jar`.

## Structure

```
.
├── pom.xml                      Maven build (artifact jade-playground 1.0-SNAPSHOT)
├── deps/JADE-all-4.5.0/         bundled JADE distribution (jars, docs, sources, JADE examples)
├── src/main/java/
│   ├── VacuumCleaner.java             JADE simple reflex agent
│   ├── ModelBasedVacuumCleaner.java   JADE model-based reflex agent
│   ├── BattleOfSexesHusband.java      JADE FIPA-Propose initiator
│   ├── BattleOfSexesWife.java         JADE FIPA-Propose responder
│   ├── BattleOfSexesGui.java          Swing window logging each agent's messages
│   ├── BattleOfSexes.java             shared game payoffs
│   ├── ContractNetLoadBalancer.java   FIPA-ContractNet initiator (task distribution)
│   ├── ContractNetWorker.java         FIPA-ContractNet responder (bids with completion time)
│   ├── ContractNetGui.java            Swing network view of the ContractNet agents
│   └── tucson/                        standalone TuCSoN coordination examples
├── APDescription.txt, MTPs-Main-Container.txt   JADE platform description / MTP addresses
└── target/                      build output
```

## JADE agent examples

The vacuum-cleaner agents live in a two-location world (**A** and **B**), act on a
periodic tick (JADE `TickerBehaviour`) with the actions `IDLE`, `GO_LEFT`,
`GO_RIGHT` and `ASPIRE`, and show a Swing window with the rooms and the vacuum.
Locations become dirty with a probability that grows with the time since they
were last cleaned.

### `VacuumCleaner` (simple reflex agent)

The agent has full observability: it senses its position and the dirt state of both locations.
Rule: if the current location is dirty, aspire; otherwise, if the other location is clean, stay idle; else move to the other location.

### `ModelBasedVacuumCleaner` (model-based reflex agent)

The agent has partial observability: it perceives only the location it stands on. It keeps a bounded history of its percepts (default 30, configurable) and, from that history, builds a belief about each location, namely the mean time it takes to become dirty and the probability that it is dirty now.
Rule: if the current location is dirty, aspire; otherwise move to the other location only when it is predicted to be dirty (or unknown) by the time the agent arrives, and stay idle in the meantime. The GUI also shows the agent's beliefs next to the ground truth.

## Running JADE agents

After compiling, start the JADE platform with its GUI (from the project root):

```
java -cp "deps/JADE-all-4.5.0/jade/lib/jade.jar:target/jade-playground-1.0-SNAPSHOT.jar" jade.Boot -gui
```

Then, in the JADE RMA GUI, create an agent (right-click on `Main-Container` > *Start New Agent*) giving a name and the class name `VacuumCleaner` or `ModelBasedVacuumCleaner`.

Alternatively, launch an agent directly from the command line with the `-agents` option:

```
java -cp "deps/JADE-all-4.5.0/jade/lib/jade.jar:target/jade-playground-1.0-SNAPSHOT.jar" jade.Boot -gui -agents vacuum:VacuumCleaner
java -cp "deps/JADE-all-4.5.0/jade/lib/jade.jar:target/jade-playground-1.0-SNAPSHOT.jar" jade.Boot -gui -agents vacuum:ModelBasedVacuumCleaner
```

The model-based agent accepts an optional maximum history length: `-agents "vacuum:ModelBasedVacuumCleaner(50)"`.

### JADE negotiation: Battle of the sexes (FIPA-Propose)

This example follows the negotiation on slide 41. The husband proposes a schedule of
activities for the next weekends; the wife can accept it or reject it with a
counteroffer. Each activity contributes to both agents' utilities: the husband
prefers movies (2 points) to friends (1), while the wife prefers friends (2) to
movies (1). The wife accepts when both utilities are at least 4, giving them a
mutually beneficial schedule. A rejected proposal ends its FIPA-Propose exchange;
the husband opens a new exchange with his next offer.

Start both agents on the JADE platform with:

```
java -cp "deps/JADE-all-4.5.0/jade/lib/jade.jar:target/jade-playground-1.0-SNAPSHOT.jar" jade.Boot -gui -agents "husband:BattleOfSexesHusband(wife);wife:BattleOfSexesWife(husband)"
```

Each agent opens a window with a table of the messages it sent and received
(round, direction, performative, schedule, both utilities), colour-coded by
performative (blue propose, red reject, green accept), and a status line with the
current state. The husband waits 2 seconds before each proposal so the exchange
can be followed.

The sample negotiation proposes `MOVIE`, then `MOVIE,FRIENDS`, and finally
`MOVIE,FRIENDS,FRIENDS`. The last schedule is accepted with utilities 4 for
the husband and 5 for the wife.

### JADE task distribution: Load balancing (FIPA-ContractNet)

A load balancer (`ContractNetLoadBalancer`, a `ContractNetInitiator`) periodically
generates tasks, each with an amount of work and a deadline, and sends a CFP
(`taskId;work;deadlineMs`) to all the processing agents (`ContractNetWorker`)
registered in the DF. Each worker has a speed (work units/s) and a backlog of
accepted tasks: it bids its estimated completion time, or refuses if it cannot
meet the deadline. The balancer accepts the earliest bid, rejects the others
and drops the task if there are no bids. The winner sends an INFORM when the
work is done. Workers handle concurrent negotiations (`SSResponderDispatcher`).

```
java -cp "deps/JADE-all-4.5.0/jade/lib/jade.jar:target/jade-playground-1.0-SNAPSHOT.jar" jade.Boot -gui -agents "w1:ContractNetWorker(100);w2:ContractNetWorker(200);lb:ContractNetLoadBalancer(10,1000)"
```

Worker argument: speed (default 100). Balancer arguments: number of tasks
(default 10) and interval between tasks in ms (default 1000). The agents log to the console.

A shared window (`ContractNetGui`) shows the network topology: the load balancer on
the left, the workers on the right, linked to it. Every ACL message is animated as a
coloured, labelled dot travelling along the link (CFP blue, PROPOSE orange, REFUSE red,
ACCEPT green, REJECT purple, INFORM teal). Workers turn orange and show a queue of
pending tasks while busy, and the balancer shows how many tasks are assigned. A log
below lists every message. The window is shared per JVM, so run all the agents in the
same container.

The protocol is slowed down by a *step delay*: workers wait that long before answering a
CFP, the balancer waits that long before accepting/rejecting the bids, and the message
dots take at least that long to travel (minimum 800 ms). It defaults to 1000 ms and can be
changed live with the slider at the top of the window (0-5000 ms), or at startup with the
JVM property `-Dcn.stepDelay=<ms>` (use 0 for full speed):

```
java -Dcn.stepDelay=2000 -cp "deps/JADE-all-4.5.0/jade/lib/jade.jar:target/jade-playground-1.0-SNAPSHOT.jar" jade.Boot -agents "w1:ContractNetWorker(100);w2:ContractNetWorker(200);lb:ContractNetLoadBalancer(10,5000)"
```

Since the pauses are blocking, use a larger task interval (second balancer argument) with large delays.

## TuCSoN coordination examples

These examples demonstrate coordination via a shared TuCSoN tuple centre, not
JADE agent behaviours or JADE ACL messaging. Each example is launched as a
standalone Java program and connects to a TuCSoN node.

### Master-worker coordination

The master-worker example runs a master and worker participants as Java threads
in one process. The master posts factorial jobs to a tuple centre with `out`;
workers compete to remove jobs atomically using `inp`, compute results, and post
them back. The master collects results in any order, then posts one stop job per
worker. Each run uses its own tuple centre. Polling is used instead of timed
suspensive `in`: in TuCSoN a timed-out `in` request can remain pending on the node.

Build and generate the runtime classpath:

```
mvn package dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

In one terminal, start a node (leave it running):

```
java -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -cp "target/classes:$(cat target/classpath.txt)" alice.tuplecentre.tucson.service.TucsonNodeService -portno 20504
```

In another terminal, launch the master and three worker participants:

```
java -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -cp "target/classes:$(cat target/classpath.txt)" tucson.MasterWorkerExample
```

Optional arguments are host, port, and worker count (1–16), in that order; for
example `tucson.MasterWorkerExample localhost 20504 4`. The fixed jobs compute
factorials from 0 to 12. The master has a 60-second deadline for result collection
and worker shutdown, checked between synchronous TuCSoN calls; it cannot interrupt
a call stalled inside the middleware. Worker failures are reported, but this
teaching example does not retry lost jobs. Stop the node with Ctrl+C when finished.

### Synchronization barrier

This example starts a coordinator and a configurable number of participant threads.
The coordinator initializes the tuple `rendezvous(N)`. On reaching the checkpoint,
each participant atomically
removes the current count with `in(rendezvous(Remaining))`, writes back
`rendezvous(Remaining-1)`, then waits with `rd(rendezvous(0))`. The final decrement
leaves the zero tuple in the tuple centre, so an agent whose `rd` is evaluated after
the tuple appears passes immediately, while earlier readers suspend until then.
Participants do not receive or need to know `N`; it is used only to initialize the tuple.

Start a TuCSoN node as described above, then launch the barrier example:

```
java -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -cp "target/classes:$(cat target/classpath.txt)" tucson.SynchronizationBarrierExample
```

Optional arguments are host, port, and number of participants (1–16), in that order.
Defaults are `localhost`, `20504`, and `3`; for example, start four participants with:

```
java -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -cp "target/classes:$(cat target/classpath.txt)" tucson.SynchronizationBarrierExample localhost 20504 4
```

#### Inspecting and stepping through the tuple centre

With the node running, use the optional Inspector mode from a terminal with
standard input and a graphical display:

```
java -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -cp "target/classes:$(cat target/classpath.txt)" tucson.MasterWorkerExample --inspect localhost 20504 3
```

The TuCSoN Inspector opens in a separate process, already attached to this run's
unique tuple centre. Before the agents start, open the Inspector's **StepMode**
tab and select **Step Mode**; then press Enter in the terminal. Use **Next Step**
to advance the tuple centre's ReSpecT VM, and the **Sets** tab's **Tuple Space**
and **Pending Ops** views to observe its state. A VM step is not necessarily a
whole job: operations and unsuccessful polling can require several steps.
Clear **Step Mode** to resume normal execution. Leave the Inspector open until
the agents finish; close it afterward to exit the example. If cancelling before
starting, close the Inspector and press Enter in the terminal.

The 60-second deadline is disabled in Inspector mode so a deliberate pause
doesn't fail the example. Uncheck **Step Mode** before closing the Inspector
during a run: otherwise the tuple centre can remain paused with agents blocked.
The Inspector may create `inspector-tuples.log`, `inspector-queries.log`, and
`inspector-reactions.log` in the working directory.
