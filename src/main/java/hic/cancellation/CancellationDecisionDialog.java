package hic.cancellation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hic.hiccell.FulfillmentStats;
import hic.priority.PrioritySheetSignals;
import hic.util.HICData;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CancellationDecisionDialog extends JDialog {
    private final List<HICData> requests;
    private final PrioritySheetSignals signals;
    private final FulfillmentStats stats;
    private final Path stateFile;
    private final Path reportDirectory;
    private final String donor;
    private final LocalDate date;
    private final DefaultTableModel orders;
    private final JTable orderTable;
    private final JTable outcomeTable;
    private final Map<String, JTextField> yieldFields = new LinkedHashMap<>();
    private final JPanel yieldPanel = new JPanel(new GridLayout(0, 3, 12, 10));
    private final Map<String, JCheckBox> yieldReady = new LinkedHashMap<>();
    private final JPanel reviewChecklist = new JPanel(new GridLayout(0, 1, 4, 6));
    private final Map<String, JCheckBox> reviewChecks = new LinkedHashMap<>();
    private final JTabbedPane steps = new JTabbedPane();
    private final DefaultTableModel allocationRows = readOnlyModel("Cell Type", "Order #", "Requester", "Decision", "Allocation", "Linked Group", "Reason", "Requester Comment");
    private final DefaultTableModel attentionRows = readOnlyModel("Order #", "Requester", "Cell Type", "Needs Your Decision", "Linked Group", "Requester Comment");
    private final DefaultTableModel inventoryRows = readOnlyModel("Cell Type", "Total Yield", "Already Delivered", "Planned / Reserved", "Remaining");
    private final JTextArea changes = new JTextArea();
    private final JLabel outcomeSummary = new JLabel();
    private final Map<String, String> previousDecisions = new LinkedHashMap<>();
    private final JLabel pbmcStatus = new JLabel();
    private final Set<String> recommendedOrders = new java.util.HashSet<>();
    private final List<Integer> cancellationOrderRows = new ArrayList<>();
    private final JTable cancellationTable;
    private final JCheckBox reviewed = new JCheckBox("I verified the donor and reviewed comments, linked groups, weekly history, and recorded outcomes.");
    private final JCheckBox minimumFirst = new JCheckBox("Reserve minimums first; distribute surplus toward maximums", true);
    private final JTextArea result = new JTextArea();
    private final DefaultTableModel cancellationRows = new DefaultTableModel(
            new Object[]{"Cell Type", "Order #", "Requester", "Minimum", "Maximum", "Why Cancel", "Requester Comment", "Will Cancel"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return column == 7; }
        @Override
        public Class<?> getColumnClass(int column) {
            return column == 7 ? Boolean.class : column == 1 ? Integer.class : column == 3 || column == 4 ? Double.class : String.class;
        }
    };
    private final JLabel cancellationSummary = new JLabel("Enter yields and calculate to see recommended cancellations.");
    private final JTextArea cancellationWarning = new JTextArea();
    private final JLabel workbookStatus = new JLabel("A highlighted priority workbook will be generated after calculation.");
    private final JButton openPriorityButton = new JButton("Open Highlighted Priority Sheet");
    private Path currentPriorityWorkbook;
    private long recommendationRevision;
    private boolean workbookGenerating;
    private String currentReport;
    private boolean outcomesDirty;

    public CancellationDecisionDialog(JFrame owner, List<HICData> requests, PrioritySheetSignals signals,
                                      FulfillmentStats stats, Path reportDirectory, String donor, LocalDate date) throws Exception {
        super(owner, "Cancellation Decision — " + donor + " — " + date, true);
        this.requests = new ArrayList<>(requests);
        this.signals = signals;
        this.stats = stats;
        this.reportDirectory = reportDirectory;
        this.donor = donor;
        this.date = date;
        String identity = donor.trim().toUpperCase(Locale.ROOT) + "|" + date;
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8)));
        stateFile = reportDirectory.toAbsolutePath().getParent().resolve("CancellationDecisionState").resolve(digest + ".json");
        restoreMissingOutcomes();
        if (this.requests.stream().map(CancellationDecisionDialog::key).distinct().count() != this.requests.size()) {
            throw new IllegalArgumentException("Duplicate requester/order/cell rows must be resolved in the preview before planning.");
        }
        orders = new DefaultTableModel(new Object[]{"Order #", "Requester", "Cell Type", "Min", "Max",
                "Protected", "Linked Group", "Donor Specific", "Filled This Week", "Recorded Status", "Delivered", "Comment"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column >= 6 && column <= 10;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 7 || column == 8 ? Boolean.class : Object.class;
            }
        };
        populateOrders();
        orderTable = new JTable(orders);
        orderTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        orderTable.setRowHeight(26);
        for (int column = 0; column < orders.getColumnCount(); column++) {
            orderTable.getColumnModel().getColumn(column).setPreferredWidth(column == 11 ? 420 : column == 1 ? 180 : 130);
        }
        orderTable.removeColumn(orderTable.getColumnModel().getColumn(10));
        orderTable.removeColumn(orderTable.getColumnModel().getColumn(9));
        orderTable.moveColumn(9, 3);
        for (int column = 0; column < orderTable.getColumnCount(); column++) {
            int modelColumn = orderTable.convertColumnIndexToModel(column);
            orderTable.getColumnModel().getColumn(column).setPreferredWidth(modelColumn == 11 ? 360
                    : modelColumn == 1 ? 160 : modelColumn == 6 ? 200 : 90);
        }
        orderTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                    boolean focused, int row, int column) {
                java.awt.Component component = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                if (!selected) component.setBackground(row % 2 == 0 ? new java.awt.Color(244, 248, 252) : java.awt.Color.WHITE);
                setToolTipText(value == null ? null : "<html><div style='width:420px'>" + escape(value.toString()) + "</div></html>");
                return component;
            }
        });
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTextArea instructions = new JTextArea("DONOR " + donor + "   •   " + date + "   •   " + requests.size() + " saved orders\n"
                + "Enter yields → verify dependencies → select cancellations and record outcomes.\n"
                + "Recommendations never cancel orders on the website. Saved priority data: " + reportDirectory.getFileName());
        instructions.setEditable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        instructions.setBackground(content.getBackground());
        instructions.setRows(3);
        instructions.setFont(instructions.getFont().deriveFont(14f));
        content.add(instructions, BorderLayout.NORTH);
        JTabbedPane tabs = steps;
        JPanel inputs = new JPanel(new BorderLayout(8, 8));
        for (String cell : this.requests.stream().map(HICData::getCellType).distinct().sorted().toList()) {
            addYieldField(cell);
        }
        JPanel yieldCard = new JPanel(new BorderLayout(12, 12));
        yieldCard.setBorder(BorderFactory.createTitledBorder("Which yields are ready?"));
        yieldCard.add(new JLabel("<html>Enter TOTAL usable yield, including amounts already distributed.<br>Use request units. Not ready = unknown; Ready with 0 = no cells.</html>"), BorderLayout.NORTH);
        yieldCard.add(yieldPanel, BorderLayout.CENTER);
        inputs.add(yieldCard, BorderLayout.NORTH);
        JPanel options = new JPanel(new GridLayout(0, 1));
        options.add(minimumFirst);
        options.add(new JLabel(stats == null
                ? "Weekly history unavailable: verify Filled This Week manually before calculating."
                : "Weekly history loaded from the saved priority workbook. Confirmed local outcomes supplement it."));
        pbmcStatus.setText("Select actual cancellations in Who to Cancel. Record deliveries in Recorded Outcomes & Deliveries. No website checks.");
        options.add(pbmcStatus);
        inputs.add(options, BorderLayout.SOUTH);
        tabs.addTab("1  Yields", inputs);
        JPanel reviewStep = new JPanel(new BorderLayout(8, 12));
        reviewStep.add(new JLabel("Review comments and linked groups below. Same group = all-or-none. CD4/CD8 pairs cannot be split."), BorderLayout.NORTH);
        reviewStep.add(new JScrollPane(orderTable), BorderLayout.CENTER);
        JPanel acknowledgements = new JPanel(new BorderLayout(8, 8));
        JScrollPane checklistScroll = new JScrollPane(reviewChecklist);
        checklistScroll.setPreferredSize(new Dimension(800, 150));
        acknowledgements.add(checklistScroll, BorderLayout.CENTER);
        reviewed.setText("I verified this donor/date and the recorded outcomes.");
        JPanel verificationActions = new JPanel(new BorderLayout(8, 8));
        verificationActions.add(reviewed, BorderLayout.CENTER);
        JButton selectAllVerification = new JButton("Select All");
        selectAllVerification.addActionListener(event -> selectAllVerification());
        verificationActions.add(selectAllVerification, BorderLayout.EAST);
        acknowledgements.add(verificationActions, BorderLayout.SOUTH);
        reviewStep.add(acknowledgements, BorderLayout.SOUTH);
        tabs.addTab("2  Review Dependencies", reviewStep);
        result.setEditable(false);
        result.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JPanel cancellations = new JPanel(new BorderLayout(8, 8));
        JPanel resultHeader = new JPanel(new GridLayout(0, 1, 0, 6));
        cancellationSummary.setFont(cancellationSummary.getFont().deriveFont(java.awt.Font.BOLD, 16));
        resultHeader.add(cancellationSummary);
        cancellationWarning.setEditable(false);
        cancellationWarning.setLineWrap(true);
        cancellationWarning.setWrapStyleWord(true);
        cancellationWarning.setRows(3);
        cancellationWarning.setBackground(new java.awt.Color(255, 246, 218));
        resultHeader.add(cancellationWarning);
        cancellations.add(resultHeader, BorderLayout.NORTH);
        cancellationTable = new JTable(cancellationRows);
        JTable cancelTable = cancellationTable;
        cancelTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        cancelTable.moveColumn(7, 0);
        cancelTable.setRowHeight(30);
        cancelTable.setAutoCreateRowSorter(true);
        cancelTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int column = 0; column < cancelTable.getColumnCount(); column++) {
            int modelColumn = cancelTable.convertColumnIndexToModel(column);
            cancelTable.getColumnModel().getColumn(column).setPreferredWidth(modelColumn == 5 ? 420
                    : modelColumn == 6 ? 360 : modelColumn == 2 ? 200 : 100);
        }
        javax.swing.table.DefaultTableCellRenderer cancelRenderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                    boolean focused, int row, int column) {
                java.awt.Component component = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                if (!selected) component.setBackground(new java.awt.Color(255, 231, 234));
                String tooltip = value == null ? "" : value.toString();
                setToolTipText("<html><div style='width:360px'>" + tooltip.replace("&", "&amp;")
                        .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</div></html>");
                return component;
            }
        };
        cancelTable.setDefaultRenderer(Object.class, cancelRenderer);
        cancelTable.setDefaultRenderer(Integer.class, cancelRenderer);
        cancelTable.setDefaultRenderer(Double.class, cancelRenderer);
        cancellations.add(new JScrollPane(cancelTable), BorderLayout.CENTER);
        cancellations.add(workbookStatus, BorderLayout.SOUTH);
        JTabbedPane recommendations = new JTabbedPane();
        recommendations.addTab("Who to Cancel", cancellations);
        JScrollPane allocationPane = tablePane(allocationRows);
        JTextArea linkedDetails = new JTextArea("Select an allocation to inspect every request in its linked group.");
        linkedDetails.setEditable(false);
        linkedDetails.setLineWrap(true);
        linkedDetails.setWrapStyleWord(true);
        JTable allocationTable = (JTable) allocationPane.getViewport().getView();
        allocationTable.getSelectionModel().addListSelectionListener(event -> {
            int selected = allocationTable.getSelectedRow();
            if (selected < 0) {
                linkedDetails.setText("Select an allocation to inspect its linked group.");
                return;
            }
            String group = String.valueOf(allocationRows.getValueAt(allocationTable.convertRowIndexToModel(selected), 5));
            StringBuilder detail = new StringBuilder("LINKED GROUP: " + group + "\n");
            for (int row = 0; row < allocationRows.getRowCount(); row++) {
                if (group.equals(String.valueOf(allocationRows.getValueAt(row, 5)))) {
                    detail.append("#").append(allocationRows.getValueAt(row, 1)).append(" ")
                            .append(allocationRows.getValueAt(row, 2)).append(" / ").append(allocationRows.getValueAt(row, 0))
                            .append(" — ").append(allocationRows.getValueAt(row, 3)).append("; allocation ")
                            .append(allocationRows.getValueAt(row, 4)).append("\n")
                            .append(allocationRows.getValueAt(row, 6)).append("\nComment: ")
                            .append(allocationRows.getValueAt(row, 7)).append("\n\n");
                }
            }
            linkedDetails.setText(detail.toString());
            linkedDetails.setCaretPosition(0);
        });
        JSplitPane allocations = new JSplitPane(JSplitPane.VERTICAL_SPLIT, allocationPane, new JScrollPane(linkedDetails));
        allocations.setResizeWeight(0.7);
        recommendations.addTab("Allocations & Linked Groups", allocations);
        JPanel attention = new JPanel(new BorderLayout(8, 8));
        attention.add(new JLabel("Resolve HOLD / REVIEW rows before distributing. Unknown-yield linked cancellations are review only."), BorderLayout.NORTH);
        attention.add(tablePane(attentionRows), BorderLayout.CENTER);
        recommendations.addTab("Needs Your Decision", attention);
        changes.setEditable(false);
        changes.setLineWrap(true);
        changes.setWrapStyleWord(true);
        recommendations.addTab("Changes Since Last Calculation", new JScrollPane(changes));
        recommendations.addTab("Full Details", new JScrollPane(result));
        JPanel recommendationStep = new JPanel(new BorderLayout(8, 12));
        JScrollPane inventory = tablePane(inventoryRows);
        inventory.setPreferredSize(new Dimension(800, 135));
        recommendationStep.add(inventory, BorderLayout.NORTH);
        recommendationStep.add(recommendations, BorderLayout.CENTER);
        tabs.addTab("3  Recommendations", recommendationStep);
        JPanel outcomes = new JPanel(new BorderLayout(8, 12));
        outcomes.add(new JLabel("<html>Record actual deliveries or correct previous outcomes here. Select cancellations in Who to Cancel.<br>FULFILLED requires actual delivered amount; PENDING / CANCELLED require 0. Save to retain outcomes across sessions.</html>"), BorderLayout.NORTH);
        outcomeTable = new JTable(orders);
        outcomeTable.setRowHeight(30);
        outcomeTable.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(new JComboBox<>(CancellationPlanner.Status.values())));
        for (int column : new int[]{11, 8, 7, 6, 5}) outcomeTable.removeColumn(outcomeTable.getColumnModel().getColumn(column));
        outcomeTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        orderTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        outcomes.add(new JScrollPane(outcomeTable), BorderLayout.CENTER);
        JPanel outcomeFooter = new JPanel(new BorderLayout(8, 8));
        outcomeFooter.add(outcomeSummary, BorderLayout.NORTH);
        outcomes.add(outcomeFooter, BorderLayout.SOUTH);
        recommendations.addTab("Recorded Outcomes & Deliveries", outcomes);
        tabs.setTitleAt(2, "3  Decisions & Outcomes");
        content.add(tabs, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = new JButton("Save Recorded Outcomes");
        save.addActionListener(event -> saveRecordedOutcomes());
        JButton calculate = new JButton("Calculate Recommendation");
        calculate.addActionListener(event -> {
            if (calculate()) {
                tabs.setSelectedIndex(2);
            }
        });
        JButton export = new JButton("Export Detailed Notes");
        export.addActionListener(event -> exportReport());
        openPriorityButton.setEnabled(false);
        openPriorityButton.addActionListener(event -> {
            try {
                if (currentPriorityWorkbook != null) java.awt.Desktop.getDesktop().open(currentPriorityWorkbook.toFile());
            } catch (Exception exception) {
                error(new IOException("Open the highlighted workbook manually:\n" + currentPriorityWorkbook + "\n" + exception.getMessage()));
            }
        });
        JButton close = new JButton("Close");
        close.addActionListener(event -> requestClose());
        JPanel outcomeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        outcomeButtons.add(save);
        JButton selectOther = new JButton("Select Other Orders…");
        selectOther.addActionListener(event -> selectOtherOrders());
        JButton saveCancellations = new JButton("Confirm & Save Selected Cancellations…");
        saveCancellations.addActionListener(event -> confirmSelectedCancellations());
        JButton selectRecommended = new JButton("Select All Listed");
        selectRecommended.addActionListener(event -> {
            for (int row = 0; row < cancellationRows.getRowCount(); row++) cancellationRows.setValueAt(true, row, 7);
        });
        JPanel cancellationFooter = new JPanel(new BorderLayout(8, 8));
        cancellationFooter.add(workbookStatus, BorderLayout.NORTH);
        JPanel cancellationActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cancellationActions.add(selectRecommended);
        cancellationActions.add(selectOther);
        cancellationActions.add(saveCancellations);
        cancellationFooter.add(cancellationActions, BorderLayout.CENTER);
        cancellationFooter.add(new JLabel("Checking a box selects intent; confirm only after cancellation actually happens. Recalculating clears selections."), BorderLayout.SOUTH);
        cancellations.add(cancellationFooter, BorderLayout.SOUTH);
        JButton back = new JButton("← Back");
        back.addActionListener(event -> tabs.setSelectedIndex(Math.max(0, tabs.getSelectedIndex() - 1)));
        JButton next = new JButton("Next →");
        next.addActionListener(event -> tabs.setSelectedIndex(Math.min(tabs.getTabCount() - 1, tabs.getSelectedIndex() + 1)));
        Runnable updateNavigation = () -> {
            int step = tabs.getSelectedIndex();
            back.setEnabled(step > 0);
            next.setVisible(step < 2);
            calculate.setVisible(step == 1 || step == 2);
            openPriorityButton.setVisible(step == 2);
            export.setVisible(step == 2);
        };
        tabs.addChangeListener(event -> updateNavigation.run());
        updateNavigation.run();
        buttons.add(back);
        buttons.add(next);
        buttons.add(calculate);
        buttons.add(openPriorityButton);
        buttons.add(export);
        buttons.add(close);
        JPanel actions = new JPanel(new GridLayout(1, 1));
        actions.add(buttons);
        outcomeFooter.add(outcomeButtons, BorderLayout.SOUTH);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        styleWorkflow(content);
        calculate.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        calculate.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        calculate.setFocusPainted(false);
        calculate.setBackground(new java.awt.Color(20, 88, 158));
        calculate.setForeground(java.awt.Color.WHITE);
        calculate.setOpaque(true);
        calculate.setFont(calculate.getFont().deriveFont(java.awt.Font.BOLD));
        saveCancellations.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        saveCancellations.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        saveCancellations.setFocusPainted(false);
        saveCancellations.setBackground(new java.awt.Color(0, 109, 79));
        saveCancellations.setForeground(java.awt.Color.WHITE);
        saveCancellations.setOpaque(true);
        saveCancellations.setFont(saveCancellations.getFont().deriveFont(java.awt.Font.BOLD));
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(new Dimension(Math.min(1380, screen.width - 60), Math.min(900, screen.height - 80)));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                requestClose();
            }
        });
        loadRecordedOutcomes();
        loadLocalWeeklyOutcomes();
        orders.addTableModelListener(event -> {
            outcomesDirty = true;
            reviewed.setSelected(false);
            rebuildReviewChecklist();
            updateOutcomeSummary();
            invalidateRecommendation();
        });
        minimumFirst.addActionListener(event -> invalidateRecommendation());
        reviewed.addActionListener(event -> invalidateRecommendation());
        rebuildReviewChecklist();
        updateOutcomeSummary();
    }

    private void populateOrders() {
        populateOrders(0);
    }

    private void addYieldField(String cell) {
        if (yieldFields.containsKey(cell)) {
            return;
        }
        yieldPanel.add(new JLabel(cell + " total yield"));
        JCheckBox ready = new JCheckBox("Ready (otherwise unknown)");
        yieldReady.put(cell, ready);
        yieldPanel.add(ready);
        JTextField amount = new JTextField();
        amount.setEnabled(false);
        amount.setToolTipText("Total usable yield in request units, including previously delivered amounts. Zero is a known zero yield.");
        yieldFields.put(cell, amount);
        yieldPanel.add(amount);
        ready.addActionListener(event -> {
            amount.setEnabled(ready.isSelected());
            if (ready.isSelected()) amount.requestFocusInWindow();
            invalidateRecommendation();
        });
        amount.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { invalidateRecommendation(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { invalidateRecommendation(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { invalidateRecommendation(); }
        });
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static JScrollPane tablePane(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(8, 2));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int column = 0; column < model.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(column == model.getColumnCount() - 1 ? 420 : 160);
        }
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable target, Object value, boolean selected,
                                                                    boolean focused, int row, int column) {
                java.awt.Component component = super.getTableCellRendererComponent(target, value, selected, focused, row, column);
                if (!selected) component.setBackground(row % 2 == 0 ? new java.awt.Color(244, 248, 252) : java.awt.Color.WHITE);
                setToolTipText(value == null ? null : "<html><div style='width:420px'>" + escape(value.toString()) + "</div></html>");
                return component;
            }
        });
        return new JScrollPane(table);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static void styleWorkflow(java.awt.Container container) {
        if (container instanceof JPanel || container instanceof JTabbedPane || container instanceof JViewport) {
            container.setBackground(new java.awt.Color(244, 248, 252));
        }
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JLabel || component instanceof JCheckBox || component instanceof JButton) {
                component.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, component.getFont().getStyle(),
                        Math.max(13, component.getFont().getSize())));
            }
            if (component instanceof JButton button) {
                button.setMargin(new java.awt.Insets(9, 14, 9, 14));
            }
            if (component instanceof JTabbedPane pane) {
                pane.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 13));
            }
            if (component instanceof JTable table) {
                table.setFillsViewportHeight(true);
                table.setShowVerticalLines(false);
                table.getTableHeader().setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 12));
            } else if (component instanceof java.awt.Container child) {
                styleWorkflow(child);
            }
        }
    }

    private void rebuildReviewChecklist() {
        reviewChecklist.removeAll();
        reviewChecks.clear();
        for (int row = 0; row < requests.size(); row++) {
            HICData request = requests.get(row);
            String comment = request.getRequesterComment();
            if (conditionalComment(comment) || donorSpecific(comment)) {
                JCheckBox check = new JCheckBox("#" + request.getOrderNumber() + " " + request.getName() + " / "
                        + request.getCellType() + " — verify linked scope and donor-specific flag");
                check.setToolTipText("<html><div style='width:500px'>" + escape(comment) + "<br>Current group: "
                        + escape(String.valueOf(orders.getValueAt(row, 6))) + "</div></html>");
                check.addActionListener(event -> invalidateRecommendation());
                reviewChecks.put(key(request), check);
                reviewChecklist.add(check);
            }
        }
        if (stats == null) {
            JCheckBox history = new JCheckBox("Weekly history unavailable — I manually verified Filled This Week for all requesters.");
            reviewChecks.put("weekly-history", history);
            history.addActionListener(event -> invalidateRecommendation());
            reviewChecklist.add(history);
        }
        if (reviewChecks.isEmpty()) reviewChecklist.add(new JLabel("No automatically flagged comments. Still review all requester comments and dependencies."));
        reviewChecklist.revalidate();
        reviewChecklist.repaint();
    }

    private void updateOutcomeSummary() {
        int fulfilled = 0;
        int cancelled = 0;
        for (int row = 0; row < orders.getRowCount(); row++) {
            String status = String.valueOf(orders.getValueAt(row, 9));
            if (status.equals("FULFILLED")) fulfilled++;
            if (status.equals("CANCELLED")) cancelled++;
        }
        outcomeSummary.setText("Donor/day outcomes: " + fulfilled + " fulfilled · " + cancelled + " cancelled · "
                + (orders.getRowCount() - fulfilled - cancelled) + " pending    |    "
                + (outcomesDirty ? "Unsaved changes — save actual outcomes before closing." : "Recorded state loaded / saved locally."));
    }

    private void showAllocationSummary(CancellationPlanner.Plan plan, Map<String, Double> yields) {
        allocationRows.setRowCount(0);
        attentionRows.setRowCount(0);
        inventoryRows.setRowCount(0);
        StringBuilder delta = new StringBuilder(previousDecisions.isEmpty()
                ? "First calculation in this session. The workbook's Decision History retains all saved iterations.\n"
                : "Changes since the previous calculation in this session:\n");
        int changed = 0;
        for (CancellationPlanner.Decision decision : plan.decisions()) {
            HICData request = decision.order().request();
            String action = decision.action();
            boolean unknown = !yields.containsKey(request.getCellType()) && decision.order().status() == CancellationPlanner.Status.PENDING;
            String display = unknown ? "NO YIELD — " + action : action;
            allocationRows.addRow(new Object[]{request.getCellType(), request.getOrderNumber(), request.getName(), display,
                    decision.allocation(), decision.order().group(), decision.reason(), request.getRequesterComment()});
            if (action.equals("HOLD") || action.equals("REVIEW") || unknown && action.equals("CANCEL")) {
                attentionRows.addRow(new Object[]{request.getOrderNumber(), request.getName(), request.getCellType(),
                        display + ": " + decision.reason(), decision.order().group(), request.getRequesterComment()});
            }
            String signature = display + " / " + CancellationPlanner.format(decision.allocation());
            String prior = previousDecisions.put(key(request), signature);
            if (prior != null && !prior.equals(signature)) {
                delta.append("#").append(request.getOrderNumber()).append(" ").append(request.getName()).append(" / ")
                        .append(request.getCellType()).append(": ").append(prior).append(" → ").append(signature).append("\n");
                changed++;
            }
        }
        plan.issues().forEach(issue -> attentionRows.addRow(new Object[]{"", "", "", issue, "", ""}));
        if (changed == 0) delta.append("No changed order decisions / allocations compared with the previous calculation, if any.\n");
        changes.setText(delta.toString());
        yields.forEach((cell, total) -> {
            double delivered = plan.decisions().stream().filter(decision -> decision.order().request().getCellType().equals(cell)
                    && decision.order().status() == CancellationPlanner.Status.FULFILLED).mapToDouble(decision -> decision.order().delivered()).sum();
            double remaining = plan.remaining().getOrDefault(cell, 0.0);
            inventoryRows.addRow(new Object[]{cell, total, delivered, total - delivered - remaining, remaining});
        });
    }


    private void populateOrders(int firstRow) {
        Map<String, List<HICData>> byPerson = requests.stream().collect(Collectors.groupingBy(
                request -> personKey(request.getName()), LinkedHashMap::new, Collectors.toList()));
        for (int index = firstRow; index < requests.size(); index++) {
            HICData request = requests.get(index);
            List<HICData> personOrders = byPerson.get(personKey(request.getName()));
            Set<String> types = personOrders.stream().map(HICData::getCellType).collect(Collectors.toSet());
            boolean cdPair = types.contains("CD4+") && types.contains("CD8+")
                    && (request.getCellType().equals("CD4+") || request.getCellType().equals("CD8+"));
            boolean conditional = personOrders.stream().anyMatch(order -> conditionalComment(order.getRequesterComment()));
            String group = conditional ? "All:" + personKey(request.getName())
                    : cdPair ? "CD4/CD8:" + personKey(request.getName()) : key(request);
            boolean weekly = stats != null && stats.requesterFilledThisWeek(request.getName());
            orders.addRow(new Object[]{request.getOrderNumber(), request.getName(), request.getCellType(),
                    request.getMinRequest(), request.getMaxRequest(), protectedOrder(request) ? "YES" : "",
                    group, donorSpecific(request.getRequesterComment()), weekly,
                    CancellationPlanner.Status.PENDING, 0.0, request.getRequesterComment()});
        }
    }

    static boolean conditionalComment(String comment) {
        String text = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
        return text.contains("both") || text.contains("all or none") || text.contains("all-or-none")
                || text.contains("together") || (text.contains("cancel") && (text.contains("if ") || text.contains("unless")))
                || text.contains("only fulfill if") || text.contains("only provide cells if");
    }

    static boolean donorSpecific(String comment) {
        String text = comment == null ? "" : comment.toLowerCase(Locale.ROOT).replace('-', ' ');
        return text.contains("donor specific") || text.contains("critical donor") || text.contains("hla")
                || text.contains("requires this donor") || text.contains("require this donor")
                || text.contains("donor specifically");
    }

    private boolean protectedOrder(HICData request) {
        return signals.isLabMember(request.getName()) || signals.isTodayRequest(request.getOrderNumber(), request.getName());
    }

    private List<CancellationPlanner.Order> readOrders() {
        if (orderTable.isEditing() && !orderTable.getCellEditor().stopCellEditing()) {
            throw new IllegalArgumentException("Finish editing the order table first.");
        }
        if (outcomeTable.isEditing() && !outcomeTable.getCellEditor().stopCellEditing()) {
            throw new IllegalArgumentException("Finish editing the actual outcome table first.");
        }
        List<CancellationPlanner.Order> result = new ArrayList<>();
        Map<String, String> cdGroups = new LinkedHashMap<>();
        for (int row = 0; row < requests.size(); row++) {
            HICData request = requests.get(row);
            String group = String.valueOf(orders.getValueAt(row, 6)).trim();
            if (group.isBlank()) {
                throw new IllegalArgumentException("Linked Group cannot be blank.");
            }
            boolean hasCdPair = requests.stream().anyMatch(other -> personKey(other.getName()).equals(personKey(request.getName()))
                    && other.getCellType().equals("CD4+"))
                    && requests.stream().anyMatch(other -> personKey(other.getName()).equals(personKey(request.getName()))
                    && other.getCellType().equals("CD8+"));
            if (hasCdPair && (request.getCellType().equals("CD4+") || request.getCellType().equals("CD8+"))) {
                String existing = cdGroups.putIfAbsent(personKey(request.getName()), group);
                if (existing != null && !existing.equals(group)) {
                    throw new IllegalArgumentException("Keep all CD4/CD8 requests for " + request.getName() + " in the same linked group.");
                }
            }
            result.add(new CancellationPlanner.Order(request, group, protectedOrder(request),
                    Boolean.TRUE.equals(orders.getValueAt(row, 7)), Boolean.TRUE.equals(orders.getValueAt(row, 8)),
                    CancellationPlanner.Status.valueOf(String.valueOf(orders.getValueAt(row, 9))),
                    number(orders.getValueAt(row, 10))));
        }
        return result;
    }

    private boolean calculate() {
        if (workbookGenerating) {
            error(new IOException("Wait for the cancellation workbook to finish saving before calculating again."));
            return false;
        }
        invalidateRecommendation();
        try {
            List<CancellationPlanner.Order> currentOrders = readOrders();
            if (!reviewed.isSelected()) {
                steps.setSelectedIndex(1);
                throw new IllegalArgumentException("Complete step 2: verify the donor/date and recorded outcomes.");
            }
            if (reviewChecks.values().stream().anyMatch(check -> !check.isSelected())) {
                steps.setSelectedIndex(1);
                throw new IllegalArgumentException("Review each flagged dependency / history item in step 2 and check its acknowledgement.");
            }
            Map<String, Double> yields = new LinkedHashMap<>();
            yieldFields.forEach((cell, field) -> {
                if (yieldReady.get(cell).isSelected()) {
                    if (field.getText().isBlank()) throw new IllegalArgumentException("Enter a yield for " + cell + " or mark it Not ready.");
                    yields.put(cell, number(field.getText()));
                }
            });
            if (yields.isEmpty()) {
                throw new IllegalArgumentException("Enter at least one actual yield.");
            }
            CancellationPlanner.Plan plan = new CancellationPlanner().plan(currentOrders, yields, stats, minimumFirst.isSelected());
            StringBuilder text = new StringBuilder("Cancellation Decision — donor " + donor + " — " + date + "\n\n");
            text.append("RECOMMENDATION ONLY. Recorded outcomes are not changed.\n")
                    .append("Priority-first heuristic: weekly access, donor specificity, then existing priority ranking.\n")
                    .append("Not a guarantee of the global maximum number of orders or weekly fulfillment.\n")
                    .append("Blank yields are unknown. HOLD allocations are provisional, not approval to distribute.\n")
                    .append("Allocation basis: ").append(minimumFirst.isSelected() ? "minimum first, surplus toward maximum" : "maximum requested").append("\n\n");
            text.append("A zero minimum uses the maximum requested amount.\n");
            text.append(stats == null ? "Saved fulfillment history unavailable; weekly flags were reviewed manually and/or loaded locally.\n"
                    : "Priority and fulfillment history loaded from the donor workbook; weekly flags include reviewed local outcomes.\n");
            text.append("\nPBMC outcomes: manually confirmed local records; no website check. Pending is not assumed fulfilled.\n");
            yields.forEach((cell, amount) -> text.append(cell).append(" total yield: ").append(CancellationPlanner.format(amount)).append("\n"));
            if (!plan.issues().isEmpty()) {
                text.append("\nUNRESOLVED — manual review required before acting:\n");
                plan.issues().forEach(issue -> text.append("- ").append(issue).append("\n"));
            }
            for (CancellationPlanner.Decision decision : plan.decisions()) {
                HICData request = decision.order().request();
                boolean unenteredCancellation = decision.action().equals("CANCEL") && !yields.containsKey(request.getCellType());
                text.append("\n").append(unenteredCancellation ? "LINKED — NO YIELD ENTERED (review only)" : decision.action()).append(" | #").append(request.getOrderNumber())
                        .append(" | ").append(request.getName()).append(" | ").append(request.getCellType())
                        .append(" | allocation ").append(CancellationPlanner.format(decision.allocation()))
                        .append("\n  ").append(decision.reason()).append("\n  Group: ").append(decision.order().group());
                if (decision.order().donorSpecific()) {
                    text.append(" | donor-specific");
                }
                if (request.getRequesterComment() != null && !request.getRequesterComment().isBlank()) {
                    text.append("\n  Comment: ").append(request.getRequesterComment());
                }
                text.append("\n");
            }
            text.append("\nRemaining after recorded distributions and planned reservations:\n");
            plan.remaining().forEach((cell, amount) -> text.append(cell).append(": ").append(CancellationPlanner.format(amount)).append("\n"));
            Set<String> covered = plan.decisions().stream()
                    .filter(decision -> decision.order().filledThisWeek() || decision.action().equals("FULFILL"))
                    .map(decision -> personKey(decision.order().request().getName())).collect(Collectors.toSet());
            List<String> unmet = plan.decisions().stream().map(decision -> decision.order().request().getName())
                    .filter(name -> !covered.contains(personKey(name))).distinct().toList();
            if (!unmet.isEmpty()) {
                text.append("\nWeekly access target would remain unmet / unresolved for:\n");
                unmet.forEach(name -> text.append("- ").append(name).append("\n"));
            }
            currentReport = text.toString();
            result.setText(currentReport);
            result.setCaretPosition(0);
            showCancellations(plan, yields);
            showAllocationSummary(plan, yields);
            generatePriorityWorkbook(plan, Map.copyOf(yields));
            return true;
        } catch (Exception exception) {
            error(exception);
            return false;
        }
    }


    private void selectAllVerification() {
        reviewChecks.values().forEach(check -> check.setSelected(true));
        reviewed.setSelected(true);
        invalidateRecommendation();
    }

    private void addCancellationRow(int sourceRow, String reason, boolean selected) {
        if (cancellationOrderRows.contains(sourceRow)) return;
        HICData request = requests.get(sourceRow);
        cancellationOrderRows.add(sourceRow);
        cancellationRows.addRow(new Object[]{request.getCellType(), request.getOrderNumber(), request.getName(),
                request.getMinRequest(), request.getMaxRequest(), reason, request.getRequesterComment(), selected});
    }

    private DefaultTableModel otherOrderRows() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Select", "Order #", "Requester", "Cell Type", "Protected", "Requester Comment"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return column == 0; }
            @Override
            public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : column == 1 ? Integer.class : String.class; }
        };
        for (int sourceRow : otherOrderSourceRows()) {
            HICData request = requests.get(sourceRow);
            model.addRow(new Object[]{false, request.getOrderNumber(), request.getName(), request.getCellType(),
                    protectedOrder(request) ? "YES — protected" : "", request.getRequesterComment()});
        }
        return model;
    }

    private List<Integer> otherOrderSourceRows() {
        return java.util.stream.IntStream.range(0, requests.size())
                .filter(row -> String.valueOf(orders.getValueAt(row, 9)).equals("PENDING") && !cancellationOrderRows.contains(row))
                .boxed().toList();
    }

    private void selectOtherOrders() {
        try {
            readOrders();
            List<Integer> sourceRows = otherOrderSourceRows();
            if (sourceRows.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No other pending orders. Previously completed outcomes can be corrected in Recorded Outcomes & Deliveries.");
                return;
            }
            DefaultTableModel model = otherOrderRows();
            JTable table = new JTable(model);
            table.setRowHeight(30);
            table.setAutoCreateRowSorter(true);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            for (int column = 0; column < table.getColumnCount(); column++) {
                table.getColumnModel().getColumn(column).setPreferredWidth(column == 5 ? 420 : column == 2 ? 200 : 140);
            }
            JPanel panel = new JPanel(new BorderLayout(8, 12));
            panel.add(new JLabel("<html><b>Add other orders to Who to Cancel</b><br>All remaining pending orders are available, including cell types without entered yields.<br>These are manual selections, not program recommendations. Protected orders are marked. Nothing is recorded yet.</html>"), BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(Math.min(1100, getWidth() - 100), 320));
            panel.add(scroll, BorderLayout.CENTER);
            if (JOptionPane.showConfirmDialog(this, panel, "Select Other Orders", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
            for (int row = 0; row < model.getRowCount(); row++) {
                if (Boolean.TRUE.equals(model.getValueAt(row, 0))) addCancellationRow(sourceRows.get(row),
                        "MANUAL SELECTION — not recommended by the current plan. Verify dependencies and protections.", true);
            }
            cancellationSummary.setText(cancellationRows.getRowCount() + " listed order(s) — check Will Cancel, then confirm actual cancellations.");
        } catch (Exception exception) {
            error(exception);
        }
    }

    private List<Integer> selectedCancellationRows() {
        List<Integer> selected = new ArrayList<>();
        for (int row = 0; row < cancellationRows.getRowCount(); row++) {
            if (Boolean.TRUE.equals(cancellationRows.getValueAt(row, 7))) selected.add(cancellationOrderRows.get(row));
        }
        return selected;
    }

    private void confirmSelectedCancellations() {
        try {
            if (cancellationTable.isEditing() && !cancellationTable.getCellEditor().stopCellEditing()) return;
            readOrders();
            List<Integer> selected = selectedCancellationRows();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Check Will Cancel for the orders you want to confirm. Unchecked orders remain unchanged.");
                return;
            }
            JTextArea confirmation = new JTextArea("Have these " + selected.size() + " cancellations ACTUALLY happened for " + donor + " / " + date + "?\n"
                    + "Yes saves CANCELLED with zero delivered and clears the old recommendation. No website orders are changed.\n"
                    + "Unchecked orders remain unchanged, NOT fulfilled. This also saves current recorded outcomes and reviewed flags.\n\n"
                    + selected.stream().map(row -> "#" + requests.get(row).getOrderNumber() + " " + requests.get(row).getName()
                            + " / " + requests.get(row).getCellType()
                            + (protectedOrder(requests.get(row)) ? " — PROTECTED: confirm this really happened" : "")
                            + (!recommendedOrders.contains(key(requests.get(row))) ? " — manually selected" : ""))
                            .collect(Collectors.joining("\n")));
            confirmation.setEditable(false);
            JScrollPane scroll = new JScrollPane(confirmation);
            scroll.setPreferredSize(new Dimension(750, 280));
            if (JOptionPane.showConfirmDialog(this, scroll, "Confirm Actual Cancellations", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            recordCancellations(selected);
            JOptionPane.showMessageDialog(this, "Actual cancellations saved. Review linked dependencies and recalculate before acting on the next recommendation.");
        } catch (Exception exception) {
            error(new IOException("Could not save cancellations: " + exception.getMessage()
                    + "\nIf outcomes show unsaved changes, correct them and use Save Recorded Outcomes.", exception));
        }
    }

    private void recordCancellations(List<Integer> selected) throws IOException {
        new CancellationPlanner().plan(readOrders(), Map.of(), stats, minimumFirst.isSelected());
        for (int row : selected) {
            if (!String.valueOf(orders.getValueAt(row, 9)).equals("PENDING")) {
                throw new IllegalArgumentException("Only pending orders can be newly confirmed as cancelled.");
            }
        }
        if (selected.isEmpty()) return;
        for (int row : selected) {
            orders.setValueAt(CancellationPlanner.Status.CANCELLED, row, 9);
            orders.setValueAt(0.0, row, 10);
        }
        persistRecordedOutcomes(readOrders());
    }

    private void saveRecordedOutcomes() {
        try {
            List<CancellationPlanner.Order> records = readOrders();
            new CancellationPlanner().plan(records, Map.of(), stats, minimumFirst.isSelected());
            int choice = JOptionPane.showConfirmDialog(this,
                    "Save the recorded FULFILLED/CANCELLED statuses and delivered quantities for this donor/date?\n"
                            + "Recommendations are not actual outcomes. Only record distributions or cancellations that happened.",
                    "Save Recorded Outcomes", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            persistRecordedOutcomes(records);
            JOptionPane.showMessageDialog(this, "Recorded outcomes saved to:\n" + stateFile
                    + "\nUse this same output directory and donor/date when reopening the planner.");
        } catch (Exception exception) {
            error(exception);
        }
    }

    private void persistRecordedOutcomes(List<CancellationPlanner.Order> records) throws IOException {
        Map<String, SavedOrder> state = new LinkedHashMap<>();
        for (CancellationPlanner.Order order : records) {
            state.put(key(order.request()), new SavedOrder(order.group(), order.donorSpecific(),
                    order.filledThisWeek(), order.status().name(), order.delivered(),
                    order.request().getMinRequest(), order.request().getMaxRequest(),
                    order.request().getOrderNumber(), order.request().getName(), order.request().getCellType(),
                    order.request().getRequestDate().toString(), order.request().getRequesterComment(),
                    order.request().getRequestType(), date.toString()));
        }
        Files.createDirectories(stateFile.getParent());
        Path temporary = Files.createTempFile(stateFile.getParent(), "outcomes-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(state));
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        outcomesDirty = false;
        updateOutcomeSummary();
    }

    private void loadRecordedOutcomes() throws IOException {
        if (!Files.exists(stateFile)) {
            return;
        }
        try {
            var state = com.google.gson.JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject();
            Gson gson = new Gson();
            for (int row = 0; row < requests.size(); row++) {
                HICData request = requests.get(row);
                if (!state.has(key(request))) {
                    continue;
                }
                SavedOrder saved = gson.fromJson(state.get(key(request)), SavedOrder.class);
                if (saved.minimum() != request.getMinRequest() || saved.maximum() != request.getMaxRequest()) {
                    throw new IOException("Saved quantities differ for #" + request.getOrderNumber() + ". Review the saved state: " + stateFile);
                }
                orders.setValueAt(saved.group(), row, 6);
                orders.setValueAt(saved.donorSpecific(), row, 7);
                orders.setValueAt(saved.filledThisWeek() || Boolean.TRUE.equals(orders.getValueAt(row, 8)), row, 8);
                orders.setValueAt(CancellationPlanner.Status.valueOf(saved.status()), row, 9);
                orders.setValueAt(saved.delivered(), row, 10);
            }
        } catch (RuntimeException exception) {
            throw new IOException("Could not read saved decision state. Review " + stateFile, exception);
        }
    }

    private void restoreMissingOutcomes() throws IOException {
        if (!Files.exists(stateFile)) {
            return;
        }
        try {
            var state = com.google.gson.JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject();
            Set<String> current = requests.stream().map(CancellationDecisionDialog::key).collect(Collectors.toSet());
            Gson gson = new Gson();
            for (var entry : state.entrySet()) {
                SavedOrder saved = gson.fromJson(entry.getValue(), SavedOrder.class);
                if (!current.contains(entry.getKey()) && !saved.status().equals(CancellationPlanner.Status.PENDING.name())) {
                    HICData restored = new HICData(requests.size() + 1, saved.requestId(),
                            java.time.LocalDateTime.parse(saved.requestDate()), saved.name(), saved.cellType(),
                            saved.maximum(), saved.minimum(), "", saved.requestType(), saved.comment());
                    if (!key(restored).equals(entry.getKey())) {
                        throw new IOException("Saved order identity does not match: " + stateFile);
                    }
                    requests.add(restored);
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Could not restore recorded outcomes from " + stateFile, exception);
        }
    }

    private void loadLocalWeeklyOutcomes() throws IOException {
        if (!Files.isDirectory(stateFile.getParent())) {
            return;
        }
        LocalDate weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        Set<String> fulfilled = new java.util.HashSet<>();
        try (var paths = Files.newDirectoryStream(stateFile.getParent(), "*.json")) {
            Gson gson = new Gson();
            for (Path path : paths) {
                var state = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                for (var element : state.entrySet()) {
                    SavedOrder saved = gson.fromJson(element.getValue(), SavedOrder.class);
                    LocalDate recordedDate = LocalDate.parse(saved.collectionDate());
                    if (!recordedDate.isBefore(weekStart) && !recordedDate.isAfter(date)
                            && saved.status().equals(CancellationPlanner.Status.FULFILLED.name()) && saved.delivered() > 0) {
                        fulfilled.add(personKey(saved.name()));
                    }
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Could not read local weekly outcome history in " + stateFile.getParent(), exception);
        }
        for (int row = 0; row < requests.size(); row++) {
            if (fulfilled.contains(personKey(requests.get(row).getName()))) {
                orders.setValueAt(true, row, 8);
            }
        }
    }

    private void exportReport() {
        try {
            if (orderTable.isEditing()) {
                orderTable.getCellEditor().stopCellEditing();
            }
            if (currentReport == null) {
                throw new IllegalArgumentException("Calculate a current recommendation before exporting.");
            }
            Files.createDirectories(reportDirectory);
            Path output = Files.createTempFile(reportDirectory, "Cancellation_Decision_" + date + "_"
                    + donor.replaceAll("[^A-Za-z0-9_-]", "_") + "_", ".txt");
            Files.writeString(output, currentReport);
            JOptionPane.showMessageDialog(this, "Saved recommendation to:\n" + output);
        } catch (Exception exception) {
            error(exception);
        }
    }

    private void invalidateRecommendation() {
        recommendationRevision++;
        currentReport = null;
        currentPriorityWorkbook = null;
        openPriorityButton.setEnabled(false);
        cancellationRows.setRowCount(0);
        cancellationOrderRows.clear();
        recommendedOrders.clear();
        allocationRows.setRowCount(0);
        attentionRows.setRowCount(0);
        inventoryRows.setRowCount(0);
        changes.setText("Inputs changed. Recalculate to compare with the last recommendation in this session.");
        cancellationSummary.setText("Inputs changed — calculate to see current cancellations.");
        cancellationWarning.setText("Recommendations are limited to entered yields. Select Other Orders can add manual exceptions; checking a box does not record an outcome.");
        workbookStatus.setText("Recalculate to generate a current highlighted priority workbook.");
        workbookStatus.setToolTipText(null);
        result.setText("Inputs changed. Calculate a new recommendation before exporting or acting.");
    }

    private void showCancellations(CancellationPlanner.Plan plan, Map<String, Double> yields) {
        List<CancellationPlanner.Decision> cancellations = CancellationRecommendationWorkbook.cancellations(plan, yields);
        cancellationRows.setRowCount(0);
        cancellationOrderRows.clear();
        recommendedOrders.clear();
        for (CancellationPlanner.Decision decision : cancellations) {
            HICData request = decision.order().request();
            recommendedOrders.add(key(request));
            addCancellationRow(requests.indexOf(request), decision.reason(), false);
        }
        cancellationSummary.setText(cancellations.isEmpty() ? "No cancellations recommended for the entered cell types."
                : cancellations.size() + " order(s) recommended for cancellation — "
                    + cancellations.stream().map(decision -> personKey(decision.order().request().getName())).distinct().count()
                    + " requester(s)");
        long unenteredLinked = plan.decisions().stream().filter(decision -> decision.action().equals("CANCEL")
                && !yields.containsKey(decision.order().request().getCellType())).count();
        String warnings = plan.issues().isEmpty() ? "Recommendations only. No website orders have been cancelled."
                : "UNRESOLVED SHORTAGE — review Full Decision Details before acting. " + String.join(" ", plan.issues());
        cancellationWarning.setText("Entered yields: " + String.join(", ", yields.keySet()) + ". " + warnings
                + (unenteredLinked > 0 ? " " + unenteredLinked + " linked order(s) have no entered yield; see details before handling those cell types." : ""));
        cancellationWarning.setCaretPosition(0);
    }

    private void generatePriorityWorkbook(CancellationPlanner.Plan plan, Map<String, Double> yields) {
        long revision = recommendationRevision;
        workbookGenerating = true;
        workbookStatus.setText("Appending this decision to the donor's cancellation workbook…");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return CancellationRecommendationWorkbook.write(reportDirectory, donor, date, plan, yields);
            }

            @Override
            protected void done() {
                workbookGenerating = false;
                if (!isDisplayable() || revision != recommendationRevision) return;
                try {
                    currentPriorityWorkbook = get();
                    openPriorityButton.setEnabled(true);
                    workbookStatus.setText("Highlighted workbook saved: " + currentPriorityWorkbook.getFileName());
                    workbookStatus.setToolTipText(currentPriorityWorkbook.toString());
                } catch (Exception exception) {
                    workbookStatus.setText("Could not generate the priority workbook. The cancellation list above remains available; recalculate to retry.");
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    error(new IOException("Could not generate highlighted priority workbook:\n" + cause.getMessage()));
                }
            }
        }.execute();
    }

    private void requestClose() {
        if (orderTable.isEditing() && !orderTable.getCellEditor().stopCellEditing()) {
            return;
        }
        if (outcomeTable.isEditing() && !outcomeTable.getCellEditor().stopCellEditing()) return;
        if (outcomesDirty || !selectedCancellationRows().isEmpty()) {
            int choice = JOptionPane.showOptionDialog(this,
                    "Recorded outcomes / reviewed flags have unsaved changes, or cancellation selections are not confirmed.\nSave outcomes or confirm actual cancellations to retain them. Checking Will Cancel alone does not record an outcome.",
                    "Unsaved Decision Outcomes", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, new String[]{"Keep Editing", "Discard and Close"}, "Keep Editing");
            if (choice != 1) {
                return;
            }
        }
        dispose();
    }

    private void error(Exception exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), "Cancellation Decision", JOptionPane.WARNING_MESSAGE);
    }

    private static double number(Object value) {
        double result = Double.parseDouble(String.valueOf(value).trim());
        if (!Double.isFinite(result) || result < 0) {
            throw new IllegalArgumentException("Quantities must be finite, nonnegative numbers.");
        }
        return result;
    }

    private static String personKey(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String key(HICData request) {
        return request.getOrderNumber() + "|" + personKey(request.getName()) + "|" + request.getCellType();
    }

    private record SavedOrder(String group, boolean donorSpecific, boolean filledThisWeek, String status,
                              double delivered, double minimum, double maximum, int requestId, String name,
                              String cellType, String requestDate, String comment, String requestType, String collectionDate) {
    }
}
