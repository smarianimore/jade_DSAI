# JADE code examples

JADE code examples for the Data Science and Artificial Intelligence course 
of the Digital Automation Engineering master's degree 
at the University of Modena and Reggio Emilia (Italy).

Course home page: [https://moodle.unimore.it/course/view.php?id=22084](https://moodle.unimore.it/course/view.php?id=22084)

## Dependencies

- **Java** (JDK 8 or later, with Swing/AWT support since the examples open a GUI)
- **Maven** 3.x
- **JADE 4.5.0**: the distribution is already bundled in `deps/JADE-all-4.5.0/`. The Maven build references `deps/JADE-all-4.5.0/jade/lib/jade.jar` as a `system`-scoped dependency, so no download is needed.

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
│   ├── VacuumCleaner.java             simple reflex agent
│   └── ModelBasedVacuumCleaner.java   model-based reflex agent
├── APDescription.txt, MTPs-Main-Container.txt   JADE platform description / MTP addresses
└── target/                      build output
```

## Example agents

Both agents live in a two-location world (**A** and **B**), act on a periodic tick (JADE `TickerBehaviour`) with the actions `IDLE`, `GO_LEFT`, `GO_RIGHT` and `ASPIRE`, and show a Swing window with the rooms and the vacuum. Locations become dirty with a probability that grows with the time since they were last cleaned.

### `VacuumCleaner` (simple reflex agent)

The agent has full observability: it senses its position and the dirt state of both locations.
Rule: if the current location is dirty, aspire; otherwise, if the other location is clean, stay idle; else move to the other location.

### `ModelBasedVacuumCleaner` (model-based reflex agent)

The agent has partial observability: it perceives only the location it stands on. It keeps a bounded history of its percepts (default 30, configurable) and, from that history, builds a belief about each location, namely the mean time it takes to become dirty and the probability that it is dirty now.
Rule: if the current location is dirty, aspire; otherwise move to the other location only when it is predicted to be dirty (or unknown) by the time the agent arrives, and stay idle in the meantime. The GUI also shows the agent's beliefs next to the ground truth.

## Running the examples

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
