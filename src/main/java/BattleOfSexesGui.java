import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/** Swing view of one agent's side of the negotiation: a table of the messages sent and received. */
class BattleOfSexesGui extends JFrame {
    private static final String[] COLUMNS =
            {"Round", "Direction", "Performative", "Schedule", "Husband utility", "Wife utility"};

    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel status = new JLabel("Waiting...", JLabel.CENTER);

    private BattleOfSexesGui(String title) {
        super(title);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setFont(table.getFont().deriveFont(14f));
        table.setDefaultRenderer(Object.class, new RowRenderer());
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);

        status.setFont(status.getFont().deriveFont(Font.BOLD, 16f));
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(status, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setPreferredSize(new Dimension(760, 320));
        pack();
        setLocationByPlatform(true);
        setVisible(true);
    }

    static BattleOfSexesGui open(String title) {
        final BattleOfSexesGui[] holder = new BattleOfSexesGui[1];
        try {
            SwingUtilities.invokeAndWait(() -> holder[0] = new BattleOfSexesGui(title));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return holder[0];
    }

    /** Appends a message to the table; direction is "sent" or "received". */
    void log(int round, String direction, String performative, String schedule) {
        final boolean valid = BattleOfSexes.isValidSchedule(schedule);
        final Object[] row = {
                round, direction, performative, schedule,
                valid ? BattleOfSexes.husbandUtility(schedule) : "-",
                valid ? BattleOfSexes.wifeUtility(schedule) : "-"
        };
        SwingUtilities.invokeLater(() -> model.addRow(row));
    }

    void setStatus(String text) {
        SwingUtilities.invokeLater(() -> status.setText(text));
    }

    void close() {
        SwingUtilities.invokeLater(this::dispose);
    }

    /** Colours rows by performative: blue proposal, red rejection, green acceptance. */
    private static class RowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            String performative = String.valueOf(table.getValueAt(row, 2));
            Color color = Color.WHITE;
            if (performative.startsWith("ACCEPT")) {
                color = new Color(200, 240, 200);
            } else if (performative.startsWith("REJECT")) {
                color = new Color(250, 215, 215);
            } else if (performative.startsWith("PROPOSE")) {
                color = new Color(220, 235, 255);
            }
            c.setBackground(selected ? table.getSelectionBackground() : color);
            return c;
        }
    }
}
