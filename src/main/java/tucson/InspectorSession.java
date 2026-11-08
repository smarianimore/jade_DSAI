package tucson;

import alice.tuplecentre.respect.api.TupleCentreId;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

final class InspectorSession {
    private final Process process;

    private InspectorSession(Process process) {
        this.process = process;
    }

    static InspectorSession start(TupleCentreId centre, String host, int port) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Inspector requires a graphical display");
        }
        List<String> command = new ArrayList<>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("alice.tuplecentre.tucson.introspection.tools.InspectorGUI");
        command.add("-tcname");
        command.add(centre.getLocalName());
        command.add("-netid");
        command.add(host);
        command.add("-portno");
        command.add(Integer.toString(port));
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        return new InspectorSession(process);
    }

    void awaitStart() throws IOException {
        System.out.println("In the Inspector, open StepMode and enable Step Mode if desired.");
        System.out.println("Press Enter here to start the agents (close the Inspector, then press Enter, to cancel).");
        if (new BufferedReader(new InputStreamReader(System.in)).readLine() == null) {
            throw new IOException("Cannot start inspection: standard input closed");
        }
        if (!process.isAlive()) {
            throw new IllegalStateException("Inspector exited before the example started");
        }
    }

    void awaitClose() throws InterruptedException {
        if (process.isAlive()) {
            System.out.println("Close the Inspector window to finish.");
            process.waitFor();
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Inspector exited with status " + process.exitValue());
        }
    }

    void stop() {
        if (process.isAlive()) {
            process.destroy();
        }
    }
}
