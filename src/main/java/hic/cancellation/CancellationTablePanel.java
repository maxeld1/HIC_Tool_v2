package hic.cancellation;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

final class CancellationTablePanel extends JPanel {
    private final JTable table;
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> cellTypes = new JComboBox<>();
    private final JLabel count = new JLabel();
    private final JTextArea detail = new JTextArea(4, 20);
    private final TableRowSorter<TableModel> sorter;
    private final int cellColumn;

    CancellationTablePanel(JTable table, int cellColumn, int... detailColumns) {
        this(table, cellColumn, true, detailColumns);
    }

    CancellationTablePanel(JTable table, int cellColumn, boolean showDetails, int... detailColumns) {
        super(new BorderLayout(0, 8));
        this.table = table;
        this.cellColumn = cellColumn;
        sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setRowHeight(28);
        table.setSelectionBackground(new java.awt.Color(214, 233, 250));
        table.setSelectionForeground(new java.awt.Color(20, 44, 67));
        table.setIntercellSpacing(new Dimension(8, 2));
        table.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 13));
        table.getTableHeader().setReorderingAllowed(false);
        List<TableColumn> columns = new ArrayList<>();
        for (int index = 0; index < table.getColumnCount(); index++) {
            TableColumn column = table.getColumnModel().getColumn(index);
            columns.add(column);
            String name = table.getModel().getColumnName(column.getModelIndex());
            column.setMinWidth(name.equals("Requester") ? 140 : 65);
            column.setPreferredWidth(name.equals("Requester") ? 180
                    : name.equals("Why Cancel") || name.equals("Needs Your Decision") ? 320
                    : name.equals("Linked Group") ? 190 : 100);
            if (table.getModel().getColumnClass(column.getModelIndex()) == Boolean.class || name.equals("Protected")) {
                column.setMaxWidth(120);
            } else if (name.equals("Order #") || name.equals("Min") || name.equals("Max")
                    || name.equals("Minimum") || name.equals("Maximum") || name.equals("Allocation") || name.equals("Delivered")) {
                column.setMaxWidth(100);
            }
        }
        JCheckBox moreColumns = new JCheckBox("More columns");
        Runnable updateColumns = () -> {
            if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
            while (table.getColumnCount() > 0) table.removeColumn(table.getColumnModel().getColumn(0));
            for (TableColumn column : columns) {
                boolean hidden = java.util.Arrays.stream(detailColumns).anyMatch(index -> index == column.getModelIndex());
                if (moreColumns.isSelected() || !hidden) table.addColumn(column);
            }
        };
        moreColumns.addActionListener(event -> updateColumns.run());
        updateColumns.run();
        search.setToolTipText("Search every field, including comments and columns not currently shown. Multiple words must all match.");
        cellTypes.setToolTipText("Filter by cell type. Hidden rows remain unchanged; Select All actions still apply to the full list.");
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.add(new JLabel("Search"));
        toolbar.add(search);
        toolbar.add(cellTypes);
        toolbar.add(moreColumns);
        JButton clear = new JButton("Clear");
        clear.addActionListener(event -> {
            search.setText("");
            cellTypes.setSelectedIndex(0);
        });
        toolbar.add(clear);
        toolbar.add(count);
        add(toolbar, BorderLayout.NORTH);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 13));
        detail.setMargin(new java.awt.Insets(8, 10, 8, 10));
        JScrollPane details = new JScrollPane(detail);
        details.setBorder(BorderFactory.createTitledBorder("Selected order — complete details"));
        details.setMinimumSize(new Dimension(100, 65));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), details);
        split.setResizeWeight(0.8);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(showDetails ? split : new JScrollPane(table), BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(event -> updateDetail());
        table.getModel().addTableModelListener(event -> SwingUtilities.invokeLater(() -> {
            refreshCellTypes();
            updateCount();
            updateDetail();
        }));
        sorter.addRowSorterListener(event -> updateCount());
        cellTypes.addActionListener(event -> filter());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { filter(); }
            public void removeUpdate(DocumentEvent event) { filter(); }
            public void changedUpdate(DocumentEvent event) { filter(); }
        });
        refreshCellTypes();
        updateCount();
        updateDetail();
    }

    private void refreshCellTypes() {
        Object selected = cellTypes.getSelectedItem();
        TreeSet<String> values = new TreeSet<>();
        for (int row = 0; row < table.getModel().getRowCount(); row++) {
            values.add(String.valueOf(table.getModel().getValueAt(row, cellColumn)));
        }
        List<String> expected = new ArrayList<>();
        expected.add("All cell types");
        expected.addAll(values);
        List<String> current = new ArrayList<>();
        for (int index = 0; index < cellTypes.getItemCount(); index++) current.add(cellTypes.getItemAt(index));
        if (current.equals(expected)) return;
        cellTypes.setModel(new DefaultComboBoxModel<>(expected.toArray(String[]::new)));
        cellTypes.setSelectedItem(expected.contains(selected) ? selected : expected.get(0));
        filter();
    }

    private void filter() {
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        Object selected = cellTypes.getSelectedItem();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                if (selected != null && !selected.equals("All cell types")
                        && !selected.equals(entry.getStringValue(cellColumn))) return false;
                StringBuilder text = new StringBuilder();
                for (int column = 0; column < entry.getValueCount(); column++) text.append(entry.getStringValue(column)).append(' ');
                String normalized = text.toString().toLowerCase(Locale.ROOT);
                for (String word : query.split("\\s+")) if (!normalized.contains(word)) return false;
                return true;
            }
        });
        updateCount();
    }

    private void updateCount() {
        count.setText(table.getRowCount() + " / " + table.getModel().getRowCount() + " orders");
        count.setToolTipText("Visible / total orders. Filtering only changes the view, never outcomes or calculations.");
    }

    private void updateDetail() {
        int selected = table.getSelectedRow();
        if (selected < 0 || selected >= table.getRowCount()) {
            detail.setText(table.getModel().getRowCount() == 0 ? "No orders to display yet."
                    : "Select an order to read its full comment, explanation, and other fields. Drag the divider to resize this area.");
            return;
        }
        int row = table.convertRowIndexToModel(selected);
        StringBuilder text = new StringBuilder();
        for (int column = 0; column < table.getModel().getColumnCount(); column++) {
            String name = table.getModel().getColumnName(column);
            if (name.equals("Requester") || name.equals("Order #") || name.equals("Cell Type")) {
                text.append(table.getModel().getValueAt(row, column)).append("   ·   ");
            }
        }
        text.append('\n');
        for (int column = 0; column < table.getModel().getColumnCount(); column++) {
            String name = table.getModel().getColumnName(column);
            if (isLongField(name)) appendField(text, row, column);
        }
        for (int column = 0; column < table.getModel().getColumnCount(); column++) {
            if (!isLongField(table.getModel().getColumnName(column))) appendField(text, row, column);
        }
        detail.setText(text.toString());
        detail.setCaretPosition(0);
    }

    private static boolean isLongField(String name) {
        return name.equals("Comment") || name.equals("Requester Comment") || name.equals("Why Cancel")
                || name.equals("Reason") || name.equals("Needs Your Decision");
    }

    private void appendField(StringBuilder text, int row, int column) {
        Object value = table.getModel().getValueAt(row, column);
        if (value == null || value.toString().isBlank()) return;
        text.append(table.getModel().getColumnName(column)).append(": ").append(value).append('\n');
    }
}
