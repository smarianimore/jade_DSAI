import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing view of the ContractNet network, shared by all the agents of the container: the load balancer
 * sits on the left, the workers on the right, and every ACL message is drawn as a coloured dot
 * travelling along the link between sender and receiver. Safe to call from any thread.
 */
class ContractNetGui extends JFrame {

    private static final long MIN_TRAVEL_MS = 800;
    private static final int MAX_DELAY_MS = 5000;
    /** Pause inserted before each protocol step; initial value from -Dcn.stepDelay, adjustable with the slider. */
    private static volatile int stepDelayMs = Integer.getInteger("cn.stepDelay", 1000);
    private static final long TRAIL_MS = 400;
    private static ContractNetGui instance;

    private static class Node {
        final String name;
        final boolean balancer;
        int tasks;
        String status = "";

        Node(String name, boolean balancer) {
            this.name = name;
            this.balancer = balancer;
        }
    }

    private static class Flight {
        final String from, to, label;
        final Color color;
        final long start = System.currentTimeMillis();
        final long travelMs = Math.max(MIN_TRAVEL_MS, stepDelayMs);

        Flight(String from, String to, String label, Color color) {
            this.from = from;
            this.to = to;
            this.label = label;
            this.color = color;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Flight> flights = new ArrayList<>();
    private final JTextArea log = new JTextArea(7, 60);
    private final NetworkPanel panel = new NetworkPanel();

    private ContractNetGui() {
        super("ContractNet load balancing");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createTitledBorder("Messages"));
        JSlider slider = new JSlider(0, MAX_DELAY_MS, Math.min(stepDelayMs, MAX_DELAY_MS));
        JLabel sliderLabel = new JLabel();
        slider.addChangeListener(e -> {
            stepDelayMs = slider.getValue();
            sliderLabel.setText("Step delay: " + stepDelayMs + " ms");
        });
        sliderLabel.setText("Step delay: " + stepDelayMs + " ms");
        slider.setMajorTickSpacing(1000);
        slider.setPaintTicks(true);
        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        controls.add(sliderLabel, BorderLayout.WEST);
        controls.add(slider, BorderLayout.CENTER);
        add(controls, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(scroll, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(820, 640));
        pack();
        setLocationByPlatform(true);
        new Timer(30, e -> panel.repaint()).start();
        setVisible(true);
    }

    private static synchronized ContractNetGui get() {
        if (instance == null || !instance.isDisplayable()) {
            final ContractNetGui[] holder = new ContractNetGui[1];
            try {
                SwingUtilities.invokeAndWait(() -> holder[0] = new ContractNetGui());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            instance = holder[0];
        }
        return instance;
    }

    /** Current pause (ms) that agents wait before each protocol step, to let the animation be followed. */
    static int stepDelayMs() {
        return stepDelayMs;
    }

    /** Blocks the calling agent for the configured step delay. */
    static void pause() {
        try {
            Thread.sleep(stepDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void addNode(String name, boolean balancer) {
        ContractNetGui g = get();
        SwingUtilities.invokeLater(() -> g.nodes.put(name, new Node(name, balancer)));
    }

    static void removeNode(String name) {
        ContractNetGui g = get();
        SwingUtilities.invokeLater(() -> g.nodes.remove(name));
    }

    /** Sets the text shown under a node, e.g. its number of pending tasks. */
    static void setNodeState(String name, int tasks, String status) {
        ContractNetGui g = get();
        SwingUtilities.invokeLater(() -> {
            Node n = g.nodes.get(name);
            if (n != null) {
                n.tasks = tasks;
                n.status = status;
            }
        });
    }

    /** Animates a message from one agent to another and appends it to the log. */
    static void message(String from, String to, String performative, String content) {
        ContractNetGui g = get();
        SwingUtilities.invokeLater(() -> {
            g.flights.add(new Flight(from, to, performative, colorOf(performative)));
            g.log.append(from + " -> " + to + "  " + performative + "  " + content + "\n");
            g.log.setCaretPosition(g.log.getDocument().getLength());
        });
    }

    static Color colorOf(String performative) {
        switch (performative) {
            case "CFP": return new Color(30, 90, 200);
            case "PROPOSE": return new Color(230, 140, 0);
            case "REFUSE": return new Color(200, 40, 40);
            case "ACCEPT": return new Color(30, 150, 60);
            case "REJECT": return new Color(140, 60, 140);
            case "INFORM": return new Color(0, 150, 150);
            default: return Color.GRAY;
        }
    }

    private class NetworkPanel extends JPanel {
        private static final int R = 38;

        NetworkPanel() {
            setBackground(Color.WHITE);
        }

        private Map<String, int[]> positions() {
            Map<String, int[]> pos = new LinkedHashMap<>();
            List<Node> workers = new ArrayList<>();
            for (Node n : nodes.values()) {
                if (n.balancer) {
                    pos.put(n.name, new int[]{110, (getHeight() - 30) / 2});
                } else {
                    workers.add(n);
                }
            }
            for (int i = 0; i < workers.size(); i++) {
                int y = (int) ((i + 0.5) * (getHeight() - 30) / workers.size());
                pos.put(workers.get(i).name, new int[]{getWidth() - 140, y});
            }
            return pos;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Map<String, int[]> pos = positions();
            FontMetrics fm = g.getFontMetrics();

            // Links
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(190, 190, 190));
            for (Node a : nodes.values()) {
                if (!a.balancer) {
                    continue;
                }
                for (Node b : nodes.values()) {
                    if (!b.balancer) {
                        g.drawLine(pos.get(a.name)[0], pos.get(a.name)[1], pos.get(b.name)[0], pos.get(b.name)[1]);
                    }
                }
            }

            // Nodes
            for (Node n : nodes.values()) {
                int[] p = pos.get(n.name);
                g.setColor(n.balancer ? new Color(200, 220, 255) : n.tasks > 0 ? new Color(255, 225, 170) : new Color(215, 240, 215));
                g.fillRoundRect(p[0] - R, p[1] - R / 2 - 6, 2 * R, R + 12, 16, 16);
                g.setColor(Color.DARK_GRAY);
                g.drawRoundRect(p[0] - R, p[1] - R / 2 - 6, 2 * R, R + 12, 16, 16);
                g.setFont(getFont().deriveFont(Font.BOLD, 13f));
                fm = g.getFontMetrics();
                g.drawString(n.name, p[0] - fm.stringWidth(n.name) / 2, p[1] - 2);
                g.setFont(getFont().deriveFont(11f));
                fm = g.getFontMetrics();
                String role = n.balancer ? "load balancer" : "worker";
                g.drawString(role, p[0] - fm.stringWidth(role) / 2, p[1] + 12);
                if (!n.status.isEmpty()) {
                    g.drawString(n.status, p[0] - fm.stringWidth(n.status) / 2, p[1] + R / 2 + 22);
                }
                if (!n.balancer) {
                    for (int i = 0; i < Math.min(n.tasks, 8); i++) {
                        g.setColor(new Color(230, 140, 0));
                        g.fillRect(p[0] - R + 4 + i * 8, p[1] - R / 2 - 18, 6, 8);
                    }
                }
            }

            // Messages in flight
            long now = System.currentTimeMillis();
            flights.removeIf(f -> now - f.start > f.travelMs + TRAIL_MS);
            g.setFont(getFont().deriveFont(Font.BOLD, 11f));
            fm = g.getFontMetrics();
            for (Flight f : flights) {
                int[] a = pos.get(f.from);
                int[] b = pos.get(f.to);
                if (a == null || b == null) {
                    continue;
                }
                double t = Math.min(1.0, (now - f.start) / (double) f.travelMs);
                // Offset replies to the side so the two directions of a link do not overlap
                double dx = b[0] - a[0], dy = b[1] - a[1];
                double len = Math.max(1, Math.hypot(dx, dy));
                double side = f.from.compareTo(f.to) < 0 ? 9 : -9;
                double ox = -dy / len * side, oy = dx / len * side;
                int x = (int) (a[0] + dx * t + ox);
                int y = (int) (a[1] + dy * t + oy);
                if (t < 1.0) {
                    g.setColor(f.color);
                    g.fillOval(x - 6, y - 6, 12, 12);
                } else {
                    g.setColor(new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), 90));
                    g.fillOval(x - 6, y - 6, 12, 12);
                }
                g.setColor(f.color.darker());
                g.drawString(f.label, x - fm.stringWidth(f.label) / 2, y - 10);
            }

            // Legend
            String[] legend = {"CFP", "PROPOSE", "REFUSE", "ACCEPT", "REJECT", "INFORM"};
            int x = 10;
            g.setFont(getFont().deriveFont(11f));
            fm = g.getFontMetrics();
            for (String l : legend) {
                g.setColor(colorOf(l));
                g.fillOval(x, getHeight() - 20, 10, 10);
                g.setColor(Color.DARK_GRAY);
                g.drawString(l, x + 14, getHeight() - 11);
                x += 14 + fm.stringWidth(l) + 16;
            }
        }
    }
}
