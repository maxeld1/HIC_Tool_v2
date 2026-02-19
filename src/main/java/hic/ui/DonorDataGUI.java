package hic.ui;

import hic.datamanagement.TXTFileParser;
import hic.logging.HICExcelLogger;
import hic.processor.HICDataNotFoundException;
import hic.processor.Processor;
import hic.util.HICData;
import hic.yield.DonorYieldRecord;
import hic.yield.GoogleSheetDonorYieldService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;

public class DonorDataGUI extends JFrame {

    private static final Color BG_COLOR = new Color(239, 245, 252);
    private static final Color CARD_COLOR = new Color(252, 254, 255);
    private static final Color PRIMARY_COLOR = new Color(20, 88, 158);
    private static final Color PRIMARY_HOVER = new Color(14, 71, 132);
    private static final Color ACCENT_COLOR = new Color(3, 134, 98);
    private static final Color ACCENT_HOVER = new Color(0, 109, 79);
    private static final Color DANGER_COLOR = new Color(176, 58, 58);
    private static final Color DANGER_HOVER = new Color(149, 44, 44);
    private static final Color TEXT_COLOR = new Color(31, 40, 52);
    private static final Color SUBTLE_TEXT = new Color(89, 100, 112);

    private static final Font HEADER_FONT = new Font("Avenir Next", Font.BOLD, 28);
    private static final Font TITLE_FONT = new Font("Avenir Next", Font.BOLD, 16);
    private static final Font BODY_FONT = new Font("Avenir Next", Font.PLAIN, 14);
    private static final Font MONO_FONT = new Font("Menlo", Font.PLAIN, 13);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String PREF_OUTPUT_DIR = "hic.output.dir";
    private static final String PREF_LABEL_TEMPLATE = "hic.template.label";
    private static final String PREF_SIGNOUT_TEMPLATE = "hic.template.signout";
    private static final String PREF_DONOR_HISTORY_SHEET_URL = "hic.donor.history.sheet.url";
    private static final String PREF_SETUP_COMPLETE = "hic.setup.complete";

    private static final String DEFAULT_OUTPUT_DIR = "Output Files";
    private static final String DEFAULT_LABEL_TEMPLATE = "HIC_Program_Label_Template.docx";
    private static final String DEFAULT_SIGNOUT_TEMPLATE = "HIC_Signout_Template.xlsx";
    private static final String DEFAULT_DONOR_HISTORY_SHEET_URL = "";
    private static final int REQUIRED_JAVA_MAJOR = 17;
    private static final String ORACLE_JDK_17_URL = "https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html";

    private static final Set<String> VALID_CELL_TYPES = Set.of(
            "CD4+", "CD8+", "Total T", "Monocytes", "PBMC", "NK Cells", "B Cells",
            "Unpurified Apheresis", "Top Layer Ficoll", "Bottom Layer Ficoll"
    );

    private JTextField donorField;
    private JTextArea dataArea;
    private JTextPane outputArea;
    private JTextArea validationArea;
    private JTextArea historyArea;
    private JTextPane donorSpecificApheresisArea;
    private JLabel runStatusValueLabel;
    private JLabel runDonorValueLabel;
    private JLabel runRecordsValueLabel;
    private JLabel runActionValueLabel;
    private JLabel runStartedValueLabel;
    private JLabel runFinishedValueLabel;
    private DefaultListModel<String> runStepsModel;
    private DefaultListModel<String> runFilesModel;
    private JLabel statusLabel;
    private JLabel recordsLabel;
    private JCheckBox dryRunCheck;
    private JButton instructionToggleButton;
    private JPanel topContainerPanel;
    private JPanel instructionPanel;

    private JTable previewTable;
    private DefaultTableModel previewModel;

    private String lastParsedInputHash = "";
    private final StringBuilder rawLogHtml = new StringBuilder();
    private int rawLogMaxLineLength = 0;
    private String lastDonorSpecificReportText = "Donor-specific report will appear here.";

    private final HICExcelLogger hicExcelLogger;
    private final Processor processor;
    private final TXTFileParser txtFileParser;
    private final GoogleSheetDonorYieldService donorYieldService;
    private final Preferences preferences;

    public DonorDataGUI(HICExcelLogger hicExcelLogger, Processor processor) {
        this.hicExcelLogger = hicExcelLogger;
        this.processor = processor;
        this.txtFileParser = new TXTFileParser();
        this.donorYieldService = new GoogleSheetDonorYieldService();
        this.preferences = Preferences.userNodeForPackage(DonorDataGUI.class);

        try {
            Image icon = ImageIO.read(Objects.requireNonNull(getClass().getResource("/HIC_LOGO.png")));
            Image resizedIcon = icon.getScaledInstance(256, 256, Image.SCALE_SMOOTH);
            setIconImage(resizedIcon);
        } catch (IOException e) {
            System.err.println("Icon image not found.");
        } catch (NullPointerException e) {
            System.err.println("Icon path not found.");
        }

        setTitle("HIC Studio");
        setSize(1320, 870);
        setMinimumSize(new Dimension(1320, 870));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_COLOR);

        buildUI();
        refreshHistoryPanel();
        setVisible(true);
        SwingUtilities.invokeLater(this::launchSetupWizardIfNeeded);
    }

    private void launchSetupWizardIfNeeded() {
        boolean setupComplete = preferences.getBoolean(PREF_SETUP_COMPLETE, false);
        if (!setupComplete) {
            openSetupWizard(true);
        }
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(BG_COLOR);

        topContainerPanel = new JPanel(new BorderLayout(0, 10));
        topContainerPanel.setOpaque(false);
        topContainerPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        instructionPanel = buildInstructionPanel();
        topContainerPanel.add(instructionPanel, BorderLayout.CENTER);
        topContainerPanel.add(buildWorkflowActionStrip(), BorderLayout.SOUTH);

        root.add(topContainerPanel, BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new GradientPanel();
        header.setLayout(new BorderLayout());
        header.setBorder(new CompoundBorder(new LineBorder(new Color(204, 218, 234), 1, true), new EmptyBorder(18, 22, 18, 22)));

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        textPanel.setOpaque(false);

        JLabel title = new JLabel("HIC Studio");
        title.setFont(HEADER_FONT);
        title.setForeground(new Color(16, 32, 48));

        JLabel subtitle = new JLabel("Preview, validate, and export HIC files with traceable run history.");
        subtitle.setFont(BODY_FONT);
        subtitle.setForeground(new Color(68, 80, 94));

        textPanel.add(title);
        textPanel.add(subtitle);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);
        instructionToggleButton = new JButton("Hide Instructions");
        instructionToggleButton.setFont(BODY_FONT);
        instructionToggleButton.setFocusPainted(false);
        instructionToggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        instructionToggleButton.addActionListener(e -> toggleInstructionsVisibility());
        controls.add(instructionToggleButton);

        header.add(textPanel, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new GridLayout(1, 2, 16, 0));
        center.setOpaque(false);
        center.add(buildLeftColumn());
        center.add(buildFeedbackCard());
        return center;
    }

    private JPanel buildInstructionPanel() {
        JPanel instructions = new JPanel(new BorderLayout(10, 0));
        instructions.setOpaque(true);
        instructions.setBackground(new Color(247, 251, 255));
        instructions.setBorder(new CompoundBorder(
                new LineBorder(new Color(206, 219, 234), 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JLabel text = new JLabel(
                "<html><div style='font-family:Avenir Next; font-size:13px; color:#33465A;'>" +
                        "<b>Quick Start:</b> Paste input and donor number, click <b>Parse & Preview</b>, " +
                        "fix validation issues, then run one action or <b>Perform All Actions</b>. " +
                        "Use <b>Dry Run</b> to simulate exports." +
                        "</div></html>"
        );
        instructions.add(text, BorderLayout.CENTER);
        return instructions;
    }

    private void toggleInstructionsVisibility() {
        boolean currentlyVisible = instructionPanel.getParent() != null;

        if (currentlyVisible) {
            topContainerPanel.remove(instructionPanel);
            instructionToggleButton.setText("Show Instructions");
        } else {
            topContainerPanel.add(instructionPanel, BorderLayout.CENTER);
            instructionToggleButton.setText("Hide Instructions");
        }

        topContainerPanel.revalidate();
        topContainerPanel.repaint();
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private JPanel buildLeftColumn() {
        JPanel leftColumn = new JPanel(new BorderLayout(0, 12));
        leftColumn.setOpaque(false);

        JPanel inputCard = buildInputCard();
        inputCard.setPreferredSize(new Dimension(620, 220));
        inputCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        leftColumn.add(inputCard, BorderLayout.NORTH);
        leftColumn.add(buildPreviewCard(), BorderLayout.CENTER);
        return leftColumn;
    }

    private JPanel buildWorkflowActionStrip() {
        JPanel strip = new JPanel(new BorderLayout(10, 0));
        strip.setOpaque(true);
        strip.setBackground(new Color(247, 251, 255));
        strip.setBorder(new CompoundBorder(
                new LineBorder(new Color(206, 219, 234), 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JPanel buttons = new JPanel(new GridLayout(1, 6, 8, 0));
        buttons.setOpaque(false);

        JButton summaryButton = createActionButton("Get HIC Summary", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Get HIC Summary", "Generated HIC summary", this::getHICSummary));
        JButton apheresisButton = createActionButton("Calculate Apheresis", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Calculate Apheresis", "Calculated apheresis", this::calculateApheresis));
        JButton labelButton = createActionButton("Make Labels", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Make Labels", "Generated labels", this::makeLabels));
        JButton signOutButton = createActionButton("Export Sign-Out Sheet", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Export Sign-Out Sheet", "Exported sign-out sheet", this::exportToSignOutSheet));
        JButton performAllButton = createActionButton("Perform All Actions", ACCENT_COLOR, ACCENT_HOVER, e -> runActionQuiet("Completed full workflow", this::performAllActions));
        JButton clearOutputButton = createActionButton("Clear Output", DANGER_COLOR, DANGER_HOVER, e -> {
            rawLogHtml.setLength(0);
            rawLogMaxLineLength = 0;
            outputArea.setText(renderRawLogHtml());
            validationArea.setText("");
            lastDonorSpecificReportText = "Donor-specific report will appear here.";
            donorSpecificApheresisArea.setText(renderRichBlock(lastDonorSpecificReportText));
            resetRunSummary();
            setStatus("Output cleared.");
        });

        buttons.add(summaryButton);
        buttons.add(apheresisButton);
        buttons.add(labelButton);
        buttons.add(signOutButton);
        buttons.add(performAllButton);
        buttons.add(clearOutputButton);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        dryRunCheck = new JCheckBox("Dry Run");
        dryRunCheck.setFont(BODY_FONT);
        dryRunCheck.setOpaque(false);
        dryRunCheck.setForeground(new Color(45, 56, 67));

        JButton settingsButton = new JButton("Settings");
        settingsButton.setFont(BODY_FONT);
        settingsButton.setFocusPainted(false);
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsButton.addActionListener(e -> openSettingsDialog());

        controls.add(dryRunCheck);
        controls.add(settingsButton);

        strip.add(buttons, BorderLayout.CENTER);
        strip.add(controls, BorderLayout.EAST);
        return strip;
    }

    private JPanel buildInputCard() {
        JPanel card = createCard("Data Input", javax.swing.border.TitledBorder.CENTER);
        card.setLayout(new BorderLayout(0, 12));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);

        JPanel donorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        donorPanel.setOpaque(false);

        JLabel donorLabel = new JLabel("Donor Number");
        donorLabel.setFont(TITLE_FONT);
        donorLabel.setForeground(TEXT_COLOR);

        donorField = new JTextField(10);
        donorField.setFont(BODY_FONT);
        donorField.setToolTipText("Required for labels and sign-out exports");

        donorPanel.add(donorLabel);
        donorPanel.add(donorField);

        JButton parseButton = createActionButton("Parse & Preview", PRIMARY_COLOR, PRIMARY_HOVER, e -> parseAndPreview());
        parseButton.setPreferredSize(new Dimension(160, 34));

        topRow.add(donorPanel, BorderLayout.WEST);
        topRow.add(parseButton, BorderLayout.EAST);

        dataArea = new JTextArea();
        dataArea.setFont(MONO_FONT);
        dataArea.setLineWrap(true);
        dataArea.setWrapStyleWord(true);
        dataArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        dataArea.setRows(10);
        dataArea.setColumns(40);

        dataArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markInputChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markInputChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markInputChanged();
            }
        });

        JScrollPane dataScroll = new JScrollPane(dataArea);
        dataScroll.setBorder(new LineBorder(new Color(212, 223, 235), 1, true));
        dataScroll.setPreferredSize(new Dimension(560, 230));

        card.add(topRow, BorderLayout.NORTH);
        card.add(dataScroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildActionsCard() {
        JPanel card = createCard("Workflow Actions");
        card.setLayout(new BorderLayout(0, 14));

        JPanel grid = new JPanel(new GridLayout(4, 2, 10, 10));
        grid.setOpaque(false);

        JButton summaryButton = createActionButton("Get HIC Summary", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Get HIC Summary", "Generated HIC summary", this::getHICSummary));
        JButton apheresisButton = createActionButton("Calculate Apheresis", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Calculate Apheresis", "Calculated apheresis", this::calculateApheresis));
        JButton exportUnsortedButton = createActionButton("Export Unsorted", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Export Unsorted", "Exported unsorted data", this::exportToExcelUnsorted));
        JButton exportSortedButton = createActionButton("Export Sorted", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Export Sorted", "Exported sorted data", this::exportToExcelSorted));
        JButton labelButton = createActionButton("Make Labels", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Make Labels", "Generated labels", this::makeLabels));
        JButton signOutButton = createActionButton("Export Sign-Out Sheet", PRIMARY_COLOR, PRIMARY_HOVER, e -> runAction("Export Sign-Out Sheet", "Exported sign-out sheet", this::exportToSignOutSheet));
        JButton performAllButton = createActionButton("Perform All Actions", ACCENT_COLOR, ACCENT_HOVER, e -> runAction("Perform All Actions", "Completed full workflow", this::performAllActions));
        JButton clearOutputButton = createActionButton("Clear Output", DANGER_COLOR, DANGER_HOVER, e -> {
            outputArea.setText("");
            validationArea.setText("");
            setStatus("Output cleared.");
        });

        grid.add(summaryButton);
        grid.add(apheresisButton);
        grid.add(exportUnsortedButton);
        grid.add(exportSortedButton);
        grid.add(labelButton);
        grid.add(signOutButton);
        grid.add(performAllButton);
        grid.add(clearOutputButton);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controls.setOpaque(false);
        controls.setBorder(new EmptyBorder(2, 0, 0, 0));

        dryRunCheck = new JCheckBox("Dry Run (validate + simulate exports)");
        dryRunCheck.setFont(BODY_FONT);
        dryRunCheck.setOpaque(false);
        dryRunCheck.setForeground(new Color(45, 56, 67));

        JButton settingsButton = new JButton("Settings");
        settingsButton.setFont(BODY_FONT);
        settingsButton.setFocusPainted(false);
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsButton.addActionListener(e -> openSettingsDialog());

        controls.add(dryRunCheck);
        controls.add(settingsButton);

        card.add(grid, BorderLayout.NORTH);
        card.add(controls, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPreviewCard() {
        JPanel card = createCard("Parsed Preview (Editable)", javax.swing.border.TitledBorder.CENTER);
        card.setLayout(new BorderLayout(0, 10));

        previewModel = new DefaultTableModel(new Object[]{"Row", "Order #", "Request Date", "Name", "Cell Type", "Max", "Min"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0;
            }
        };

        previewTable = new JTable(previewModel);
        previewTable.setFont(BODY_FONT);
        previewTable.setRowHeight(24);
        previewTable.setGridColor(new Color(224, 232, 240));
        previewTable.setSelectionBackground(new Color(221, 236, 248));
        previewTable.setSelectionForeground(TEXT_COLOR);

        JTableHeader header = previewTable.getTableHeader();
        header.setFont(new Font("Avenir Next", Font.BOLD, 13));
        header.setBackground(new Color(231, 240, 249));
        header.setForeground(new Color(33, 47, 61));

        JScrollPane tableScroll = new JScrollPane(previewTable);
        tableScroll.setBorder(new LineBorder(new Color(212, 223, 235), 1, true));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        JButton reparseButton = createActionButton("Re-Parse from Input", PRIMARY_COLOR, PRIMARY_HOVER, e -> parseAndPreview());
        JButton clearTableButton = createActionButton("Clear Table", DANGER_COLOR, DANGER_HOVER, e -> {
            previewModel.setRowCount(0);
            recordsLabel.setText("Parsed records: 0");
            setStatus("Preview table cleared.");
        });

        actions.add(reparseButton);
        actions.add(clearTableButton);

        card.add(actions, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFeedbackCard() {
        JPanel card = createCard("HIC Summary & Apheresis", javax.swing.border.TitledBorder.CENTER);
        card.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(BODY_FONT);

        outputArea = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        outputArea.setEditable(false);
        outputArea.setContentType("text/html");
        outputArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        outputArea.setText(renderRawLogHtml());
        outputArea.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                outputArea.setText(renderRawLogHtml());
            }
        });

        validationArea = new JTextArea();
        validationArea.setEditable(false);
        validationArea.setFont(MONO_FONT);
        validationArea.setLineWrap(true);
        validationArea.setWrapStyleWord(true);
        validationArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        validationArea.setRows(14);
        validationArea.setColumns(48);

        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(MONO_FONT);
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        historyArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        historyArea.setRows(14);
        historyArea.setColumns(48);

        donorSpecificApheresisArea = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        donorSpecificApheresisArea.setEditable(false);
        donorSpecificApheresisArea.setContentType("text/html");
        donorSpecificApheresisArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        donorSpecificApheresisArea.setText(renderRichBlock(lastDonorSpecificReportText));
        donorSpecificApheresisArea.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                donorSpecificApheresisArea.setText(renderDonorSpecificHtml(lastDonorSpecificReportText));
                donorSpecificApheresisArea.setCaretPosition(0);
            }
        });

        tabs.addTab("Output", buildRunSummaryPanel());
        JScrollPane rawLogScroll = new JScrollPane(outputArea);
        rawLogScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rawLogScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JScrollPane donorSpecificScroll = new JScrollPane(donorSpecificApheresisArea);
        donorSpecificScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        donorSpecificScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        tabs.addTab("Summary", rawLogScroll);
        tabs.addTab("Donor Specific Apheresis", donorSpecificScroll);
        tabs.addTab("Validation", new JScrollPane(validationArea));
        tabs.addTab("History", new JScrollPane(historyArea));

        card.add(tabs, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRunSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setBackground(Color.WHITE);

        JPanel meta = new JPanel(new GridLayout(2, 3, 10, 8));
        meta.setOpaque(false);

        runStatusValueLabel = createSummaryValueLabel("Ready");
        runDonorValueLabel = createSummaryValueLabel("-");
        runRecordsValueLabel = createSummaryValueLabel("0");
        runActionValueLabel = createSummaryValueLabel("-");
        runStartedValueLabel = createSummaryValueLabel("-");
        runFinishedValueLabel = createSummaryValueLabel("-");

        meta.add(createSummaryTile("Status", runStatusValueLabel));
        meta.add(createSummaryTile("Donor", runDonorValueLabel));
        meta.add(createSummaryTile("Parsed Records", runRecordsValueLabel));
        meta.add(createSummaryTile("Last Action", runActionValueLabel));
        meta.add(createSummaryTile("Started", runStartedValueLabel));
        meta.add(createSummaryTile("Finished", runFinishedValueLabel));

        runStepsModel = new DefaultListModel<>();
        runFilesModel = new DefaultListModel<>();

        JList<String> stepsList = new JList<>(runStepsModel);
        stepsList.setFont(BODY_FONT);
        JScrollPane stepsScroll = new JScrollPane(stepsList);
        stepsScroll.setBorder(BorderFactory.createTitledBorder("Completed Steps"));

        JList<String> filesList = new JList<>(runFilesModel);
        filesList.setFont(BODY_FONT);
        JScrollPane filesScroll = new JScrollPane(filesList);
        filesScroll.setBorder(BorderFactory.createTitledBorder("Generated Files"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stepsScroll, filesScroll);
        split.setResizeWeight(0.62);
        split.setBorder(BorderFactory.createEmptyBorder());

        panel.add(meta, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSummaryTile(String title, JLabel value) {
        JPanel tile = new JPanel(new BorderLayout(0, 4));
        tile.setOpaque(true);
        tile.setBackground(new Color(248, 251, 255));
        tile.setBorder(new CompoundBorder(new LineBorder(new Color(214, 224, 236), 1, true), new EmptyBorder(8, 10, 8, 10)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Avenir Next", Font.BOLD, 12));
        titleLabel.setForeground(new Color(74, 86, 98));

        tile.add(titleLabel, BorderLayout.NORTH);
        tile.add(value, BorderLayout.CENTER);
        return tile;
    }

    private JLabel createSummaryValueLabel(String value) {
        JLabel label = new JLabel(value);
        label.setFont(new Font("Avenir Next", Font.BOLD, 14));
        label.setForeground(new Color(32, 45, 60));
        return label;
    }

    private JPanel buildStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(false);
        statusBar.setBorder(new EmptyBorder(0, 6, 0, 6));

        statusLabel = new JLabel("Ready.");
        statusLabel.setFont(BODY_FONT);
        statusLabel.setForeground(new Color(78, 88, 98));

        recordsLabel = new JLabel("Parsed records: 0");
        recordsLabel.setFont(BODY_FONT);
        recordsLabel.setForeground(new Color(78, 88, 98));

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(recordsLabel, BorderLayout.EAST);
        return statusBar;
    }

    private JPanel createCard(String title) {
        return createCard(title, javax.swing.border.TitledBorder.LEFT);
    }

    private JPanel createCard(String title, int titleJustification) {
        JPanel card = new JPanel();
        card.setBackground(CARD_COLOR);
        Border base = new CompoundBorder(new LineBorder(new Color(204, 218, 234), 1, true), new EmptyBorder(14, 14, 14, 14));
        Border titled = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(),
                title,
                titleJustification,
                0,
                TITLE_FONT,
                new Color(42, 56, 72)
        );
        card.setBorder(new CompoundBorder(base, titled));
        return card;
    }

    private JButton createActionButton(String text, Color baseColor, Color hoverColor, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setFont(BODY_FONT);
        button.setForeground(Color.WHITE);
        button.setBackground(baseColor);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(new EmptyBorder(10, 12, 10, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(action);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(baseColor);
            }
        });
        return button;
    }

    private void markInputChanged() {
        String currentHash = hashText(dataArea.getText());
        if (!currentHash.equals(lastParsedInputHash)) {
            setStatus("Input changed. Re-parse recommended.");
        }
    }

    private void runAction(String actionName, String successStatus, Runnable action) {
        setStatus("Running...");
        beginRunSummary(actionName);
        appendOutputActionHeader(actionName);
        action.run();
        if (!"Action failed.".equals(statusLabel.getText())) {
            setStatus(successStatus);
            appendOutputStatus("SUCCESS", successStatus);
            addRunStep(successStatus);
            finishRunSummary("Success");
            appendOutputSeparator();
        }
    }

    private void runActionQuiet(String successStatus, Runnable action) {
        setStatus("Running...");
        beginRunSummary("Perform All Actions");
        action.run();
        if (!"Action failed.".equals(statusLabel.getText())) {
            setStatus(successStatus);
            addRunStep(successStatus);
            finishRunSummary("Success");
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void appendOutput(String text) {
        if (text == null) {
            return;
        }
        String normalized = text.replace("\\n", "\n");
        updateRawLogMaxLineLength(normalized);
        rawLogHtml.append(renderLogEntryHtml(normalized));
        outputArea.setText(renderRawLogHtml());
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void appendOutputSeparator() {
        rawLogHtml.append("<div class='divider'></div>");
        outputArea.setText(renderRawLogHtml());
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void appendOutputActionHeader(String actionName) {
        rawLogHtml.append("<div class='action-header'>")
                .append("<span class='action-time'>")
                .append(escapeHtml(LocalDateTime.now().format(OUTPUT_TIME_FORMATTER)))
                .append("</span>")
                .append("<span class='action-title'>")
                .append(escapeHtml(actionName))
                .append("</span></div>");
        outputArea.setText(renderRawLogHtml());
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void appendOutputStatus(String type, String message) {
        String badgeClass = switch (type) {
            case "SUCCESS" -> "badge-success";
            case "ERROR" -> "badge-error";
            default -> "badge-info";
        };
        updateRawLogMaxLineLength(type + " " + message);
        rawLogHtml.append("<div class='status-row'><span class='badge ").append(badgeClass).append("'>")
                .append(escapeHtml(type))
                .append("</span><span class='status-msg'>")
                .append(escapeHtml(message))
                .append("</span></div>");
        outputArea.setText(renderRawLogHtml());
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private String renderRawLogHtml() {
        int bodyFont = computeAutoFitFontPx(outputArea == null ? 520 : outputArea.getWidth(), rawLogMaxLineLength, 13, 9);
        int monoFont = Math.max(8, bodyFont - 1);
        return """
                <html>
                <head>
                <style>
                  body { font-family: 'Avenir Next', sans-serif; background:#f3f8fe; color:#22313f; margin:10px; }
                  .entry { background:#ffffff; border:1px solid #d8e4f0; border-radius:12px; padding:10px 12px; margin-bottom:10px; box-shadow:0 3px 12px rgba(17,43,72,0.06); }
                  .entry-table { padding:6px; overflow:hidden; }
                  .entry-table .meta-title { font-size:%dpx; font-weight:700; color:#17354d; margin-bottom:6px; white-space:nowrap; letter-spacing:0.15px; }
                  .entry-table .meta-line { font-size:%dpx; margin-bottom:4px; color:#486176; white-space:nowrap; }
                  .entry-table .meta-key { font-weight:700; color:#24384c; }
                  .entry-table .meta-value { color:#2f4860; }
                  .entry-table table { width:100%%; max-width:100%%; border-collapse:collapse; margin-top:8px; font-size:%dpx; table-layout:fixed; border:1px solid #c8d2dc; border-radius:0; overflow:hidden; background:#eef2f5; }
                  .entry-table th { text-align:left; background:#e2e7ec; color:#17354d; padding:8px 10px; border-bottom:2px solid #c8d2dc; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-weight:700; }
                  .entry-table td { padding:8px 10px; border-top:1px solid #cfd8e0; font-family: Menlo, monospace; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-size:%dpx; color:#2a3f54; }
                  .entry-table td.num { text-align:left; font-variant-numeric: tabular-nums; }
                  .entry-table tbody tr:nth-child(odd) { background:#f4f7fa; }
                  .entry-table tbody tr:nth-child(even) { background:#ecf1f5; }
                  .entry p { margin:0 0 4px 0; font-size:%dpx; line-height:1.35; white-space:nowrap; }
                  .entry p:last-child { margin-bottom:0; }
                  .entry ul { margin:4px 0 0 18px; padding:0; }
                  .entry li { margin:2px 0; font-size:%dpx; white-space:nowrap; }
                  .mono { font-family: Menlo, monospace; font-size: %dpx; white-space: pre; line-height: 1.35; margin:0; color:#2a3a49; }
                  .action-header { display:flex; align-items:center; gap:8px; margin:8px 0; flex-wrap:wrap; }
                  .action-time { font-family: Menlo, monospace; font-size:%dpx; color:#4f6477; background:#e8f0f8; border-radius:7px; padding:3px 7px; white-space:nowrap; }
                  .action-title { font-size:%dpx; font-weight:700; color:#244057; white-space:nowrap; }
                  .divider { height:1px; background:#d6e3ef; margin:7px 1px; }
                  .status-row { display:flex; align-items:center; gap:8px; background:#ffffff; border:1px solid #d6e3ef; border-radius:8px; padding:8px 10px; margin-bottom:8px; flex-wrap:wrap; }
                  .status-msg { font-size: %dpx; color:#233445; white-space:nowrap; }
                  .badge { font-family:'Avenir Next', sans-serif; font-size:11px; font-weight:700; padding:3px 8px; border-radius:12px; color:#fff; }
                  .badge-success { background:#0f8a5f; }
                  .badge-error { background:#c0392b; }
                  .badge-info { background:#2e6fa3; }
                </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(bodyFont + 1, bodyFont, bodyFont, monoFont, bodyFont, bodyFont, monoFont, monoFont, bodyFont, bodyFont, rawLogHtml);
    }

    private String renderRichBlock(String text) {
        return """
                <html><head><style>
                body { font-family:'Avenir Next', sans-serif; background:#f7fbff; color:#22313f; margin:10px; }
                .card { background:#ffffff; border:1px solid #d6e3ef; border-radius:10px; padding:12px; }
                .mono { font-family: Menlo, monospace; font-size:12px; white-space: pre-wrap; margin:0; line-height:1.35; }
                </style></head><body><div class='card'><pre class='mono'>%s</pre></div></body></html>
                """.formatted(escapeHtml(text == null ? "" : text.replace("\\n", "\n")));
    }

    private String renderLogEntryHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace("\\n", "\n").trim();
        String[] lines = normalized.split("\\R");

        String knownSummaryTable = tryRenderKnownSummaryTable(normalized);
        if (knownSummaryTable != null) {
            return knownSummaryTable;
        }

        String structuredTable = tryRenderRawLogTable(normalized);
        if (structuredTable != null) {
            return structuredTable;
        }

        if (isLikelyPreformatted(normalized)) {
            return "<div class='entry'>" + toHtmlPre(normalized) + "</div>";
        }

        StringBuilder html = new StringBuilder("<div class='entry'>");
        boolean inList = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                continue;
            }

            if (trimmed.startsWith("- ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(escapeHtml(trimmed.substring(2).trim())).append("</li>");
            } else {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<p>").append(escapeHtml(trimmed)).append("</p>");
            }
        }
        if (inList) {
            html.append("</ul>");
        }
        html.append("</div>");
        return html.toString();
    }

    private String tryRenderKnownSummaryTable(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        boolean isHicOrderSummary = normalized.contains("HIC Order Summary");
        boolean isApheresisSummary = normalized.contains("Summary of Apheresis Required");
        if (!isHicOrderSummary && !isApheresisSummary) {
            return null;
        }

        int expectedColumns = isHicOrderSummary ? 4 : 3;
        String title = isHicOrderSummary ? "HIC Order Summary" : "Summary of Apheresis Required";
        String[] headers = isHicOrderSummary
                ? new String[]{"Cell Type", "Requests", "Max", "Min"}
                : new String[]{"Cell Type", "Max", "Min"};

        String[] lines = normalized.split("\\R");
        List<String[]> rowValues = new ArrayList<>();
        StringBuilder meta = new StringBuilder();

        boolean inBody = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.matches("[-=]{5,}")) {
                continue;
            }

            if (trimmed.equals(title) || trimmed.startsWith("Cell Type")) {
                if (trimmed.startsWith("Cell Type")) {
                    inBody = true;
                }
                continue;
            }

            if (!inBody) {
                continue;
            }

            if (trimmed.startsWith("Total Orders:")) {
                String totalOrders = trimmed.replace("Total Orders:", "").trim();
                if (!totalOrders.isEmpty()) {
                    meta.append("<div class='meta-line'><span class='meta-key'>Total Orders:</span> ")
                            .append("<span class='meta-value'>").append(escapeHtml(totalOrders)).append("</span></div>");
                }
                continue;
            }

            String[] cols = parseRowByNumericTail(trimmed, expectedColumns);
            if (cols.length != expectedColumns) {
                cols = splitColumns(trimmed);
                if (cols.length != expectedColumns) {
                    continue;
                }
            }

            String[] normalizedRow = new String[headers.length];
            for (int c = 0; c < headers.length; c++) {
                normalizedRow[c] = c < cols.length ? cols[c] : "";
            }
            rowValues.add(normalizedRow);
        }

        if (rowValues.isEmpty()) {
            return null;
        }
        return renderSummaryTableCardHtml(title, meta.toString(), headers, rowValues);
    }

    private String tryRenderRawLogTable(String text) {
        String[] lines = text.split("\\R");
        int headerIndex = -1;
        String[] headers = null;
        int expectedColumns = -1;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.matches("[-=]{5,}")) {
                continue;
            }
            String[] candidate = splitColumns(lines[i]);
            if (candidate.length < 2) {
                continue;
            }
            String lower = trimmed.toLowerCase();
            if (looksLikeTableHeader(lower, candidate)) {
                headerIndex = i;
                headers = candidate;
                expectedColumns = candidate.length;
                break;
            }
        }

        if (headerIndex < 0 || headers == null || headers.length < 2) {
            return null;
        }

        StringBuilder meta = new StringBuilder();
        String metaTitle = "";
        for (int i = 0; i < headerIndex; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.matches("[-=]{5,}")) {
                continue;
            }
            if (metaTitle.isEmpty()) {
                metaTitle = trimmed;
            } else {
                meta.append("<div class='meta-line'>").append(escapeHtml(trimmed)).append("</div>");
            }
        }

        List<String[]> rowValues = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.matches("[-=]{5,}")) {
                continue;
            }

            String[] cols = splitColumns(lines[i]);
            if (cols.length < 2 || cols.length != expectedColumns) {
                cols = parseRowByNumericTail(trimmed, expectedColumns);
                if (cols.length < 2) {
                    continue;
                }
            }

            String[] normalizedRow = new String[headers.length];
            for (int c = 0; c < headers.length; c++) {
                normalizedRow[c] = c < cols.length ? cols[c] : "";
            }
            rowValues.add(normalizedRow);
        }

        if (rowValues.isEmpty()) {
            return null;
        }

        return renderSummaryTableCardHtml(metaTitle, meta.toString(), headers, rowValues);
    }

    private String renderSummaryTableCardHtml(String title, String metaHtml, String[] headers, List<String[]> rows) {
        StringBuilder html = new StringBuilder();
        boolean compact = headers != null && headers.length >= 5;
        String headerFont = compact ? "10px" : "11px";
        String cellFont = compact ? "10px" : "11px";
        String cellPad = compact ? "3px 4px" : "4px 6px";
        String titleFont = compact ? "13px" : "14px";
        String metaPad = compact ? "6px 8px" : "7px 9px";
        String[] colWidths = buildColumnWidths(headers);

        html.append("<div class='entry entry-table' style='box-sizing:border-box; width:100%; margin:10px 0 16px 0; padding:0; overflow:hidden; border:1px solid #c5d4e3; border-radius:12px; background:#f8fbff;'>");
        if (title != null && !title.isBlank()) {
            html.append("<div style='padding:8px 10px; background:#244a67; color:#ffffff; font-size:")
                    .append(titleFont)
                    .append("; font-weight:700; letter-spacing:0.1px; line-height:1.2;'>")
                    .append(escapeHtml(title))
                    .append("</div>");
        }
        if (metaHtml != null && !metaHtml.isBlank()) {
            html.append("<div style='padding:")
                    .append(metaPad)
                    .append("; background:#edf3f9; border-bottom:1px solid #d5e1ec;'>")
                    .append(styleMetaHtml(metaHtml))
                    .append("</div>");
        }

        html.append("<table cellspacing='0' cellpadding='0' ")
                .append("style='box-sizing:border-box; width:100%; border-collapse:collapse; table-layout:fixed; border:0; background:#eef2f5;'>");
        if (colWidths != null && colWidths.length > 0) {
            html.append("<colgroup>");
            for (String width : colWidths) {
                html.append("<col style='width:").append(width).append(";'>");
            }
            html.append("</colgroup>");
        }

        html.append("<thead><tr style='background:#e2e7ec;'>");
        for (String header : headers) {
            html.append("<th style='text-align:left; color:#17354d; padding:")
                    .append(cellPad)
                    .append("; border-bottom:2px solid #c8d2dc; border-top:0; font-size:")
                    .append(headerFont)
                    .append("; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; line-height:1.15;'>")
                    .append(escapeHtml(header))
                    .append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String rowColor = (rowIndex % 2 == 0) ? "#f4f7fa" : "#ecf1f5";
            String[] row = rows.get(rowIndex);
            boolean highlightTotalRow = isTotalRow(row);
            String effectiveRowColor = highlightTotalRow ? "#dceeff" : rowColor;
            html.append("<tr style='background:").append(effectiveRowColor).append(";'>");
            for (String value : row) {
                String align = isLikelyNumericValue(value) ? "left" : "left";
                html.append("<td style='padding:")
                        .append(cellPad)
                        .append("; border-top:1px solid #cfd8e0; color:#2a3f54; font-size:")
                        .append(cellFont)
                        .append("; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; text-align:")
                        .append(align)
                        .append("; line-height:1.15;")
                        .append(highlightTotalRow ? " font-weight:700;" : "")
                        .append("'>")
                        .append(escapeHtml(value))
                        .append("</td>");
            }
            html.append("</tr>");
        }

        html.append("</tbody></table></div>");
        return html.toString();
    }

    private String[] buildColumnWidths(String[] headers) {
        if (headers == null || headers.length == 0) {
            return new String[0];
        }
        if (headers.length == 6) {
            return new String[]{"30%", "14%", "14%", "14%", "14%", "14%"};
        }
        if (headers.length == 5) {
            return new String[]{"34%", "16.5%", "16.5%", "16.5%", "16.5%"};
        }
        if (headers.length == 4) {
            return new String[]{"37%", "21%", "21%", "21%"};
        }
        if (headers.length == 3) {
            return new String[]{"46%", "27%", "27%"};
        }

        String[] widths = new String[headers.length];
        double each = 100.0 / headers.length;
        for (int i = 0; i < headers.length; i++) {
            widths[i] = String.format("%.2f%%", each);
        }
        return widths;
    }

    private String styleMetaHtml(String metaHtml) {
        return metaHtml
                .replace("<div class='meta-line'>", "<div style='font-size:11px; color:#2f4a61; margin:0; line-height:1.2;'>")
                .replace("<span class='meta-key'>", "<span style='font-weight:700; color:#1f3e56;'>")
                .replace("<span class='meta-value'>", "<span style='color:#29465e;'>");
    }

    private String[] parseRowByNumericTail(String line, int expectedColumns) {
        if (line == null || expectedColumns < 2) {
            return new String[0];
        }
        int numericColumns = expectedColumns - 1;
        String[] out = new String[expectedColumns];
        String remaining = line.trim();

        for (int i = expectedColumns - 1; i >= 1; i--) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^(.*)\\s+(-?\\d+(?:\\.\\d+)?)$")
                    .matcher(remaining);
            if (!matcher.matches()) {
                return new String[0];
            }
            out[i] = matcher.group(2).trim();
            remaining = matcher.group(1).trim();
        }

        if (remaining.isEmpty()) {
            return new String[0];
        }
        out[0] = remaining;

        int foundNumeric = 0;
        for (int i = 1; i < out.length; i++) {
            if (isLikelyNumericValue(out[i])) {
                foundNumeric++;
            }
        }
        return foundNumeric == numericColumns ? out : new String[0];
    }

    private boolean looksLikeTableHeader(String headerLower, String[] columns) {
        if (columns == null || columns.length < 2) {
            return false;
        }

        if (headerLower.contains("cell type") || headerLower.contains("max") || headerLower.contains("min")
                || headerLower.contains("request") || headerLower.contains("order") || headerLower.contains("date")) {
            return true;
        }

        int shortTitleCount = 0;
        for (String column : columns) {
            String trimmed = column == null ? "" : column.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 16 && !isLikelyNumericValue(trimmed)) {
                shortTitleCount++;
            }
        }
        return shortTitleCount >= 2;
    }

    private boolean isLikelyPreformatted(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("Cell Type")
                || text.contains("TOTAL")
                || text.contains("================")
                || text.matches("(?s).*[\\t].*")
                || text.matches("(?s).*\\s{4,}.*");
    }

    private void setDonorSpecificReport(String reportText) {
        lastDonorSpecificReportText = reportText == null ? "" : reportText;
        donorSpecificApheresisArea.setText(renderDonorSpecificHtml(reportText));
        donorSpecificApheresisArea.setCaretPosition(0);
    }

    private String renderDonorSpecificHtml(String reportText) {
        String text = reportText == null ? "" : reportText.replace("\\n", "\n");
        if (text.isBlank()) {
            return renderRichBlock("No donor-specific report available.");
        }

        if ("No donor match in google sheet.".equalsIgnoreCase(text.trim())) {
            return """
                    <html><head><style>
                    html, body { margin:0; padding:0; }
                    body { font-family:'Avenir Next', sans-serif; background:#f7fbff; color:#22313f; padding:8px; }
                    .card { width:100%%; background:#ffffff; border:1px solid #d6e3ef; border-radius:10px; padding:12px; }
                    .title { font-size:15px; font-weight:700; color:#22384d; margin-bottom:8px; }
                    .pill { display:inline-block; font-size:12px; font-weight:700; color:#8a5a00; background:#fff4d6; border:1px solid #f0dfb0; border-radius:999px; padding:4px 10px; }
                    </style></head><body>
                    <div class='card'>
                      <div class='title'>Donor Specific Apheresis</div>
                      <span class='pill'>No donor match in google sheet.</span>
                    </div>
                    </body></html>
                    """;
        }

        if (!text.contains("Cell Type")) {
            return renderRichBlock(text);
        }

        String[] lines = text.split("\\R");
        StringBuilder meta = new StringBuilder();
        boolean tableStarted = false;
        String[] headers = null;
        List<String[]> rowValues = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (!tableStarted && trimmed.startsWith("Cell Type")) {
                tableStarted = true;
                headers = splitColumns(line);
                continue;
            }

            if (!tableStarted) {
                if (trimmed.contains(":")) {
                    int index = trimmed.indexOf(':');
                    String key = trimmed.substring(0, index).trim();
                    String value = trimmed.substring(index + 1).trim();
                    meta.append("<div class='meta-line'><span class='meta-key'>")
                            .append(escapeHtml(key))
                            .append(":</span> ")
                            .append("<span class='meta-value'>")
                            .append(escapeHtml(value))
                            .append("</span></div>");
                } else {
                    meta.append("<div class='meta-line'>").append(escapeHtml(trimmed)).append("</div>");
                }
                continue;
            }

            if (trimmed.matches("[-=]{5,}")) {
                continue;
            }

            String[] cols = splitColumns(line);
            if (cols.length < 2) {
                continue;
            }

            String[] normalizedRow = new String[headers.length];
            for (int i = 0; i < headers.length; i++) {
                normalizedRow[i] = i < cols.length ? cols[i] : "";
            }
            rowValues.add(normalizedRow);
        }

        if (headers == null || headers.length == 0 || rowValues.isEmpty()) {
            return renderRichBlock(text);
        }

        String cardHtml = renderSummaryTableCardHtml("Donor Specific Apheresis", meta.toString(), headers, rowValues);
        return renderRichPage(cardHtml);
    }

    private String renderRichPage(String innerHtml) {
        return "<html><body style='margin:0; padding:8px; background:#f3f8fe; font-family:\"Avenir Next\", sans-serif;'>"
                + (innerHtml == null ? "" : innerHtml)
                + "</body></html>";
    }

    private String[] splitColumns(String line) {
        return line.trim().split("\\s{2,}|\\t+");
    }

    private String toHtmlPre(String text) {
        return "<pre class='mono'>" + escapeHtml(text) + "</pre>";
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void updateRawLogMaxLineLength(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        rawLogMaxLineLength = Math.max(rawLogMaxLineLength, longestLineLength(text));
    }

    private int longestLineLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] lines = text.replace("\\n", "\n").split("\\R");
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, line.length());
        }
        return max;
    }

    private int computeAutoFitFontPx(int paneWidth, int maxLineLength, int defaultFont, int minFont) {
        if (paneWidth <= 0 || maxLineLength <= 0) {
            return defaultFont;
        }
        int usableWidth = Math.max(280, paneWidth - 44);
        double ratio = (double) usableWidth / Math.max(1.0, maxLineLength * 7.0);
        int computed = (int) Math.floor(defaultFont * Math.min(1.0, ratio));
        return Math.max(minFont, Math.min(defaultFont, computed));
    }

    private boolean isLikelyNumericValue(String value) {
        if (value == null) {
            return false;
        }
        String cleaned = value.trim().replace(",", "");
        return cleaned.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean isTotalRow(String[] row) {
        if (row == null || row.length == 0 || row[0] == null) {
            return false;
        }
        String firstColumn = row[0].trim();
        return firstColumn.equalsIgnoreCase("total")
                || firstColumn.equalsIgnoreCase("total apheresis");
    }

    private void beginRunSummary(String actionName) {
        if (runStatusValueLabel == null) {
            return;
        }
        runStatusValueLabel.setText("Running");
        runDonorValueLabel.setText(donorField.getText().trim().isEmpty() ? "-" : donorField.getText().trim().toUpperCase());
        runRecordsValueLabel.setText(String.valueOf(previewModel == null ? 0 : previewModel.getRowCount()));
        runActionValueLabel.setText(actionName);
        runStartedValueLabel.setText(LocalDateTime.now().format(OUTPUT_TIME_FORMATTER));
        runFinishedValueLabel.setText("-");
        runStepsModel.clear();
        runFilesModel.clear();
    }

    private void finishRunSummary(String status) {
        if (runStatusValueLabel == null) {
            return;
        }
        runStatusValueLabel.setText(status);
        runRecordsValueLabel.setText(String.valueOf(previewModel == null ? 0 : previewModel.getRowCount()));
        runFinishedValueLabel.setText(LocalDateTime.now().format(OUTPUT_TIME_FORMATTER));
    }

    private void failRunSummary(String actionName, String message) {
        if (runStatusValueLabel == null) {
            return;
        }
        runStatusValueLabel.setText("Failed");
        runActionValueLabel.setText(actionName);
        runFinishedValueLabel.setText(LocalDateTime.now().format(OUTPUT_TIME_FORMATTER));
        addRunStep("ERROR: " + message);
    }

    private void addRunStep(String step) {
        if (runStepsModel != null) {
            runStepsModel.addElement(step);
        }
    }

    private void addGeneratedFile(String path) {
        if (runFilesModel == null || path == null || path.isBlank()) {
            return;
        }
        if (!runFilesModel.contains(path)) {
            runFilesModel.addElement(path);
        }
    }

    private void resetRunSummary() {
        if (runStatusValueLabel == null) {
            return;
        }
        runStatusValueLabel.setText("Ready");
        runDonorValueLabel.setText("-");
        runRecordsValueLabel.setText("0");
        runActionValueLabel.setText("-");
        runStartedValueLabel.setText("-");
        runFinishedValueLabel.setText("-");
        runStepsModel.clear();
        runFilesModel.clear();
    }

    private void appendValidation(String text) {
        validationArea.setText(text);
        validationArea.setCaretPosition(0);
    }

    private void parseAndPreview() {
        try {
            String input = dataArea.getText();
            if (input == null || input.trim().isEmpty()) {
                throw new HICDataNotFoundException("No HIC Data Found. Please paste HIC input text.");
            }

            txtFileParser.parseFromString(input);
            List<HICData> parsed = txtFileParser.getHICData();
            if (parsed == null || parsed.isEmpty()) {
                throw new HICDataNotFoundException("No records parsed. Confirm the input format and try again.");
            }

            previewModel.setRowCount(0);
            for (int i = 0; i < parsed.size(); i++) {
                HICData record = parsed.get(i);
                previewModel.addRow(new Object[]{
                        i + 1,
                        record.getOrderNumber(),
                        DATE_TIME_FORMATTER.format(record.getRequestDate()),
                        record.getName(),
                        record.getCellType(),
                        record.getMaxRequest(),
                        record.getMinRequest()
                });
            }

            recordsLabel.setText("Parsed records: " + parsed.size());
            lastParsedInputHash = hashText(input);

            ValidationResult result = validateData(input, parsed);
            appendValidation(formatValidation(result));
            appendOutputStatus("INFO", "Parsed " + parsed.size() + " records into preview table.");
            runRecordsValueLabel.setText(String.valueOf(parsed.size()));
            setStatus("Parsed and validated.");
        } catch (Exception e) {
            handleActionError("Parse & Preview", e, 0, List.of());
        }
    }

    private List<HICData> prepareData(boolean donorRequired) throws HICDataNotFoundException {
        if (donorRequired && donorField.getText().trim().isEmpty()) {
            throw new HICDataNotFoundException("Donor number is required for labels and sign-out exports.");
        }

        String currentHash = hashText(dataArea.getText());
        if (previewModel.getRowCount() == 0 || !currentHash.equals(lastParsedInputHash)) {
            parseAndPreview();
            if (previewModel.getRowCount() == 0) {
                throw new HICDataNotFoundException("No parsed data available. Fix input and parse again.");
            }
        }

        List<HICData> data = collectDataFromPreviewTable();
        ValidationResult result = validateData(dataArea.getText(), data);
        appendValidation(formatValidation(result));

        if (!result.errors.isEmpty()) {
            throw new HICDataNotFoundException("Validation errors found. Review the Validation tab before running actions.");
        }

        createOutputDirectoryIfNeeded();
        return data;
    }

    private List<HICData> collectDataFromPreviewTable() throws HICDataNotFoundException {
        List<HICData> data = new ArrayList<>();
        for (int row = 0; row < previewModel.getRowCount(); row++) {
            try {
                int orderNumber = Integer.parseInt(String.valueOf(previewModel.getValueAt(row, 1)).trim());
                String requestDateRaw = String.valueOf(previewModel.getValueAt(row, 2)).trim();
                LocalDateTime requestDate = LocalDateTime.parse(requestDateRaw, DATE_TIME_FORMATTER);
                String name = String.valueOf(previewModel.getValueAt(row, 3)).trim();
                String cellType = String.valueOf(previewModel.getValueAt(row, 4)).trim();
                double max = Double.parseDouble(String.valueOf(previewModel.getValueAt(row, 5)).trim());
                double min = Double.parseDouble(String.valueOf(previewModel.getValueAt(row, 6)).trim());

                if (name.isEmpty()) {
                    throw new HICDataNotFoundException("Row " + (row + 1) + " has an empty name.");
                }

                data.add(new HICData(row + 1, orderNumber, requestDate, name, cellType, max, min));
            } catch (NumberFormatException | DateTimeParseException e) {
                throw new HICDataNotFoundException("Row " + (row + 1) + " contains invalid numeric/date values. Use yyyy-MM-dd HH:mm:ss for Request Date.");
            }
        }
        return data;
    }

    private ValidationResult validateData(String rawInput, List<HICData> data) {
        ValidationResult result = new ValidationResult();

        String[] lines = rawInput == null ? new String[0] : rawInput.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNo = i + 1;

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] tokens = line.split("\\s+");
            if (tokens.length < 6) {
                result.errors.add("Line " + lineNo + ": too few fields. Expected request id, timestamp, name, cell type, max/min values.");
                continue;
            }

            if (tokens.length >= 3) {
                try {
                    LocalDateTime.parse(tokens[1] + " " + tokens[2], DATE_TIME_FORMATTER);
                } catch (DateTimeParseException ex) {
                    result.errors.add("Line " + lineNo + ": invalid date/time format. Expected yyyy-MM-dd HH:mm:ss.");
                }
            }

            if (!lineHasRecognizedCellType(tokens)) {
                String inferredCellType = inferCellTypeFromLine(tokens);
                result.warnings.add(
                        "Line " + lineNo + ": cell type not recognized"
                                + (inferredCellType.isBlank() ? "." : " ('" + inferredCellType + "').")
                                + " Check values like CD4+, CD8+, PBMC, NK Cells, B Cells, Monocytes, Total T, Unpurified Apheresis, Top Layer Ficoll, Bottom Layer Ficoll."
                );
            }

            if (!lineHasNumericPair(tokens)) {
                result.errors.add("Line " + lineNo + ": max/min numeric pair not found.");
            }
        }

        for (HICData row : data) {
            if (!VALID_CELL_TYPES.contains(row.getCellType())) {
                result.warnings.add("Row " + row.getID() + ": non-standard cell type '" + row.getCellType() + "'.");
            }

            if (row.getMinRequest() > row.getMaxRequest()) {
                result.errors.add("Row " + row.getID() + ": Min request is greater than Max request.");
            }
        }

        if (data.isEmpty()) {
            result.errors.add("No parsed records available.");
        }

        return result;
    }

    private String formatValidation(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Report\n");
        sb.append("=================\n\n");

        if (result.errors.isEmpty()) {
            sb.append("Errors: none\n");
        } else {
            sb.append("Errors (must fix):\n");
            for (String error : result.errors) {
                sb.append("- ").append(error).append("\n");
            }
        }

        sb.append("\n");

        if (result.warnings.isEmpty()) {
            sb.append("Warnings: none\n");
        } else {
            sb.append("Warnings (review):\n");
            for (String warning : result.warnings) {
                sb.append("- ").append(warning).append("\n");
            }
        }

        sb.append("\nTip: Edit rows directly in the Preview table, then rerun actions.\n");
        return sb.toString();
    }

    private boolean lineHasRecognizedCellType(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return false;
        }

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i]
                    .replaceAll("[^A-Za-z0-9+]", "")
                    .toLowerCase();

            if (token.isBlank()) {
                continue;
            }

            if (token.equals("cd4+") || token.equals("cd8+") || token.equals("pbmc")
                    || token.equals("monocytes") || token.equals("total")
                    || token.equals("unpurified") || token.equals("top")
                    || token.equals("bottom")) {
                return true;
            }

            if (token.equals("nk") || token.equals("nkcells")) {
                return true;
            }

            if (token.equals("b") || token.equals("bcells")) {
                return true;
            }
        }

        return false;
    }

    private boolean lineHasNumericPair(String[] tokens) {
        for (int i = 0; i < tokens.length - 1; i++) {
            if (isNumeric(tokens[i]) && isNumeric(tokens[i + 1])) {
                return true;
            }
        }
        return false;
    }

    private String inferCellTypeFromLine(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return "";
        }

        int numericPairIndex = -1;
        for (int i = 0; i < tokens.length - 1; i++) {
            if (isNumeric(tokens[i]) && isNumeric(tokens[i + 1])) {
                numericPairIndex = i;
                break;
            }
        }

        int end = numericPairIndex == -1 ? tokens.length : numericPairIndex;
        if (end <= 3) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (int i = Math.max(3, end - 3); i < end; i++) {
            String token = tokens[i].replaceAll("[^A-Za-z0-9+\\-]", "").trim();
            if (token.isBlank()) {
                continue;
            }
            if (token.equalsIgnoreCase("I") || token.equalsIgnoreCase("E_A") || token.equalsIgnoreCase("E_C")) {
                continue;
            }
            parts.add(token);
        }

        return String.join(" ", parts).trim();
    }

    private boolean isNumeric(String value) {
        return value != null && value.matches("-?\\d+(\\.\\d+)?");
    }

    private void getHICSummary() {
        String action = "Get HIC Summary";
        try {
            List<HICData> data = prepareData(false);
            String summaryText = processor.getHICSummaryString(data);
            appendOutput(summaryText);
            addRunStep("Summary generated");
            writeAudit(action, true, data.size(), List.of(), "");
        } catch (Exception e) {
            handleActionError(action, e, 0, List.of());
        }
    }

    private void calculateApheresis() {
        String action = "Calculate Apheresis";
        try {
            List<HICData> data = prepareData(false);
            List<Double> maxAndMinRequests = processor.printHICSummary(data);
            String apheresisText = processor.getApheresisCalculationString(maxAndMinRequests);
            appendOutput(apheresisText);
            setDonorSpecificReport(buildDonorSpecificApheresisReport(maxAndMinRequests));
            addRunStep("Apheresis calculated");
            writeAudit(action, true, data.size(), List.of(), "");
        } catch (Exception e) {
            handleActionError(action, e, 0, List.of());
        }
    }

    private void exportToExcelUnsorted() {
        String action = "Export Unsorted";
        try {
            List<HICData> data = prepareData(false);
            String output = outputPath("UnsortedHICList.xlsx");

            if (dryRunCheck.isSelected()) {
                appendOutput("DRY RUN: Would export unsorted data to: " + output);
                addRunStep("Dry run export unsorted");
            } else {
                hicExcelLogger.logHICData(data, output, false);
                appendOutput("Exported unsorted data to: " + output);
                addRunStep("Exported unsorted data");
                addGeneratedFile(output);
            }

            writeAudit(action, true, data.size(), List.of(output), "");
        } catch (Exception e) {
            handleActionError(action, e, 0, List.of());
        }
    }

    private void exportToExcelSorted() {
        String action = "Export Sorted";
        try {
            List<HICData> data = prepareData(false);
            processor.sortByCellTypeAndDateTime(data);
            String output = outputPath("SortedHICList.xlsx");

            if (dryRunCheck.isSelected()) {
                appendOutput("DRY RUN: Would export sorted data to: " + output);
                addRunStep("Dry run export sorted");
            } else {
                hicExcelLogger.logHICData(data, output, true);
                appendOutput("Exported sorted data to: " + output);
                addRunStep("Exported sorted data");
                addGeneratedFile(output);
            }

            writeAudit(action, true, data.size(), List.of(output), "");
        } catch (Exception e) {
            handleActionError(action, e, 0, List.of());
        }
    }

    private void makeLabels() {
        String action = "Make Labels";
        try {
            List<HICData> data = prepareData(true);
            String donor = donorField.getText().trim();
            String labelTemplatePath = resolveLabelTemplatePath();

            processor.sortByCellTypeAndDateTime(data);

            String cdOutput = outputPath("CD4CD8_Labels.docx");
            String otherOutput = outputPath("OTHERCellTypes_Labels.docx");

            if (dryRunCheck.isSelected()) {
                appendOutput("DRY RUN: Would generate labels using template: " + labelTemplatePath);
                appendOutput("DRY RUN: - " + cdOutput);
                appendOutput("DRY RUN: - " + otherOutput);
                addRunStep("Dry run label generation");
            } else {
                hicExcelLogger.exportToWord(processor.getCD4CD8CellRecords(data), labelTemplatePath, cdOutput, donor);
                hicExcelLogger.exportToWord(processor.getOtherCellTypeRecords(data), labelTemplatePath, otherOutput, donor);
                appendOutput("Created labels:\n- " + cdOutput + "\n- " + otherOutput);
                addRunStep("Labels created");
                addGeneratedFile(cdOutput);
                addGeneratedFile(otherOutput);
            }

            writeAudit(action, true, data.size(), List.of(cdOutput, otherOutput), "");
        } catch (Exception e) {
            handleActionError(action, e, 0, List.of());
        }
    }

    private void exportToSignOutSheet() {
        String action = "Export Sign-Out Sheet";
        try {
            List<HICData> data = prepareData(true);
            String donor = donorField.getText().trim();
            String signOutTemplatePath = resolveSignoutTemplatePath();
            String output = outputPath("HIC Sign-out Sheet.xlsx");

            if (dryRunCheck.isSelected()) {
                appendOutput("DRY RUN: Would export sign-out sheet using template: " + signOutTemplatePath);
                appendOutput("DRY RUN: - " + output);
                addRunStep("Dry run sign-out export");
            } else {
                hicExcelLogger.exportToSignOutSheet(data, signOutTemplatePath, output, donor);
                appendOutput("Exported sign-out sheet to: " + output);
                addRunStep("Sign-out sheet exported");
                addGeneratedFile(output);
            }

            writeAudit(action, true, data.size(), List.of(output), "");
        } catch (Exception e) {
            handleActionError(action, e, 0, List.of());
        }
    }

    private void performAllActions() {
        String action = "Perform All Actions";
        try {
            List<HICData> data = prepareData(true);
            String donor = donorField.getText().trim();
            rawLogHtml.setLength(0);
            rawLogMaxLineLength = 0;
            outputArea.setText(renderRawLogHtml());

            appendOutput("Running complete workflow...\n");

            String summaryText = processor.getHICSummaryString(data);
            appendOutput(summaryText);
            List<Double> maxAndMinRequests = processor.printHICSummary(data);
            String apheresisText = processor.getApheresisCalculationString(maxAndMinRequests);
            appendOutput(apheresisText);
            setDonorSpecificReport(buildDonorSpecificApheresisReport(maxAndMinRequests));

            String unsortedOutput = outputPath("UnsortedHICList.xlsx");
            String sortedOutput = outputPath("SortedHICList.xlsx");
            String cdOutput = outputPath("CD4CD8_Labels.docx");
            String otherOutput = outputPath("OTHERCellTypes_Labels.docx");
            String signOutOutput = outputPath("HIC Sign-out Sheet.xlsx");
            String labelTemplatePath = resolveLabelTemplatePath();
            String signOutTemplatePath = resolveSignoutTemplatePath();

            if (dryRunCheck.isSelected()) {
                appendOutput("DRY RUN: Would export unsorted data to: " + unsortedOutput);
                appendOutput("DRY RUN: Would export sorted data to: " + sortedOutput);
                appendOutput("DRY RUN: Would create labels using: " + labelTemplatePath);
                appendOutput("DRY RUN: - " + cdOutput);
                appendOutput("DRY RUN: - " + otherOutput);
                appendOutput("DRY RUN: Would export sign-out sheet using: " + signOutTemplatePath);
                appendOutput("DRY RUN: - " + signOutOutput);
                addRunStep("Dry run full workflow completed");
            } else {
                hicExcelLogger.logHICData(data, unsortedOutput, false);
                addGeneratedFile(unsortedOutput);

                processor.sortByCellTypeAndDateTime(data);
                hicExcelLogger.logHICData(data, sortedOutput, true);
                addGeneratedFile(sortedOutput);

                hicExcelLogger.exportToWord(processor.getCD4CD8CellRecords(data), labelTemplatePath, cdOutput, donor);
                hicExcelLogger.exportToWord(processor.getOtherCellTypeRecords(data), labelTemplatePath, otherOutput, donor);
                addGeneratedFile(cdOutput);
                addGeneratedFile(otherOutput);

                hicExcelLogger.exportToSignOutSheet(data, signOutTemplatePath, signOutOutput, donor);
                addGeneratedFile(signOutOutput);
                addRunStep("Workflow exports completed");
            }

            appendOutput("\nAll workflow actions completed successfully.");
            appendOutput("[SUCCESS] Completed full workflow.");
            writeAudit(action, true, data.size(), List.of(unsortedOutput, sortedOutput, cdOutput, otherOutput, signOutOutput), "");
        } catch (Exception e) {
            handleActionError(action, e, 0, List.of());
        }
    }

    private void handleActionError(String action, Exception e, int recordCount, List<String> outputs) {
        String message = e.getMessage() == null ? "Unexpected error." : e.getMessage();
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        setStatus("Action failed.");
        appendOutputStatus("ERROR", action + " - " + message);
        appendOutputSeparator();
        failRunSummary(action, message);
        writeAudit(action, false, recordCount, outputs, message);
    }

    private String outputPath(String fileName) {
        return Path.of(outputDirectory(), fileName).toString();
    }

    private void createOutputDirectoryIfNeeded() {
        try {
            Files.createDirectories(Path.of(outputDirectory()));
        } catch (IOException e) {
            appendOutput("Warning: could not ensure output folder exists: " + e.getMessage());
        }
    }

    private String outputDirectory() {
        return preferences.get(PREF_OUTPUT_DIR, DEFAULT_OUTPUT_DIR);
    }

    private String donorHistorySheetUrl() {
        return preferences.get(PREF_DONOR_HISTORY_SHEET_URL, DEFAULT_DONOR_HISTORY_SHEET_URL);
    }

    private String resolveLabelTemplatePath() throws HICDataNotFoundException {
        return resolveTemplatePath(preferences.get(PREF_LABEL_TEMPLATE, DEFAULT_LABEL_TEMPLATE), DEFAULT_LABEL_TEMPLATE);
    }

    private String resolveSignoutTemplatePath() throws HICDataNotFoundException {
        return resolveTemplatePath(preferences.get(PREF_SIGNOUT_TEMPLATE, DEFAULT_SIGNOUT_TEMPLATE), DEFAULT_SIGNOUT_TEMPLATE);
    }

    private String buildDonorSpecificApheresisReport(List<Double> maxAndMinRequests) {
        String donorId = donorField.getText().trim();
        if (donorId.isBlank()) {
            return "Donor Specific Apheresis\n========================\n\nEnter donor number to enable donor-specific adjustments.";
        }

        String sheetUrl = donorHistorySheetUrl();
        if (sheetUrl.isBlank()) {
            return "Donor Specific Apheresis\n========================\n\nNo Google Sheet URL configured. Add it in Settings/Setup Wizard.\n"
                    + "Use the shared Google Sheet URL.";
        }

        try {
            List<DonorYieldRecord> latestRecords = donorYieldService.getMostRecentDonationByCellType(sheetUrl, donorId);
            if (latestRecords.isEmpty()) {
                return "No donor match in google sheet.";
            }

            Map<String, DonorYieldRecord> latestByCell = new HashMap<>();
            for (DonorYieldRecord record : latestRecords) {
                latestByCell.put(record.getCellType(), record);
            }

            Map<String, double[]> defaultApheresis = processor.calculateDefaultApheresisByCell(maxAndMinRequests);
            Map<String, double[]> requestedByCell = processor.calculateRequestedCellsByCell(maxAndMinRequests);
            Map<String, double[]> adjusted = new HashMap<>();
            String historyDate = latestRecords.get(0).getDate().toString();

            StringBuilder sb = new StringBuilder();
            sb.append("Donor Specific Apheresis\n");
            sb.append("============================================================\n");
            sb.append("Donor       : ").append(donorId.toUpperCase()).append("\n");
            sb.append("History Date: ").append(historyDate).append(" (most recent match)\n");
            sb.append("Method      : adjusted_multiplier = (40 * historical_volume) / historical_yield\n");
            sb.append("              adjusted_apheresis = requested_cells * (adjusted_multiplier / 40)\n");
            sb.append("Fallback    : default formula when history is missing/invalid\n\n");

            sb.append(String.format("%-14s %-10s %-10s %-10s %-10s %-10s%n",
                    "Cell Type", "Def Max", "Adj Max", "Def Min", "Adj Min", "Source"));
            sb.append("-------------------------------------------------------------------\n");

            for (Map.Entry<String, double[]> entry : defaultApheresis.entrySet()) {
                String cellType = entry.getKey();
                double[] defaults = entry.getValue();
                double[] requested = requestedByCell.get(cellType);
                DonorYieldRecord history = latestByCell.get(cellType);

                double adjMax = defaults[0];
                double adjMin = defaults[1];
                String source = "Default";

                if (history != null && history.getActualYield() > 0 && history.getApheresisUsed() > 0) {
                    double adjustedMultiplier = (40.0 * history.getApheresisUsed()) / history.getActualYield();
                    adjMax = requested[0] * (adjustedMultiplier / 40.0);
                    adjMin = requested[1] * (adjustedMultiplier / 40.0);
                    source = "History";
                }

                adjusted.put(cellType, new double[]{adjMax, adjMin});
                sb.append(String.format("%-14s %-10.2f %-10.2f %-10.2f %-10.2f %-10s%n",
                        cellType, defaults[0], adjMax, defaults[1], adjMin, source));
            }

            double[] defaultTotals = processor.getApheresisTotals(defaultApheresis);
            double[] adjustedTotals = processor.getApheresisTotals(adjusted);
            sb.append("-------------------------------------------------------------------\n");
            sb.append(String.format("%-14s %-10.2f %-10.2f %-10.2f %-10.2f %-10s%n",
                    "TOTAL", defaultTotals[0], adjustedTotals[0], defaultTotals[1], adjustedTotals[1], ""));

            return sb.toString();
        } catch (IOException e) {
            return "Donor Specific Apheresis\n========================\n\nUnable to read donor history sheet.\n"
                    + "Check URL/share permissions and internet connection.\nDetails: " + e.getMessage();
        }
    }

    private String resolveTemplatePath(String configuredPath, String fallbackName) throws HICDataNotFoundException {
        String normalizedConfiguredPath = normalizeConfiguredPath(configuredPath);
        Path configured = toPathOrNull(normalizedConfiguredPath);

        List<Path> candidates = new ArrayList<>();
        if (configured != null) {
            candidates.add(configured);
            if (!configured.isAbsolute()) {
                candidates.add(Path.of(System.getProperty("user.dir"), normalizedConfiguredPath));
            }
        }

        Path appDir = detectAppDirectory();
        if (appDir != null) {
            candidates.add(appDir.resolve("Templates").resolve(fallbackName));
            candidates.add(appDir.resolve(fallbackName));
        }

        candidates.add(Path.of("Templates", fallbackName));
        candidates.add(Path.of(fallbackName));
        candidates.add(Path.of(System.getProperty("user.dir"), "Templates", fallbackName));
        candidates.add(Path.of(System.getProperty("user.dir"), fallbackName));

        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }

        throw new HICDataNotFoundException(
                "Missing template: " + fallbackName + ". Configure template paths in Settings."
        );
    }

    private String normalizeConfiguredPath(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() >= 2
                && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private Path toPathOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private Path detectAppDirectory() {
        try {
            URI codeSource = DonorDataGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Path.of(codeSource).toAbsolutePath().normalize();
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private void writeAudit(String action, boolean success, int records, List<String> outputs, String error) {
        try {
            createOutputDirectoryIfNeeded();
            Path logPath = Path.of(outputDirectory(), "hic_audit_log.csv");

            if (!Files.exists(logPath)) {
                Files.writeString(logPath,
                        "timestamp,action,success,donor,records,input_hash,outputs,error\n",
                        StandardCharsets.UTF_8);
            }

            String row = String.join(",",
                    csv(LocalDateTime.now().toString()),
                    csv(action),
                    csv(String.valueOf(success)),
                    csv(donorField.getText().trim()),
                    csv(String.valueOf(records)),
                    csv(hashText(dataArea.getText())),
                    csv(String.join(" | ", outputs)),
                    csv(error == null ? "" : error)
            ) + "\n";

            Files.writeString(logPath, row, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            refreshHistoryPanel();
        } catch (Exception ignored) {
            appendOutput("Warning: could not write audit log.");
        }
    }

    private void refreshHistoryPanel() {
        try {
            Path logPath = Path.of(outputDirectory(), "hic_audit_log.csv");
            if (!Files.exists(logPath)) {
                historyArea.setText("No audit history yet.");
                return;
            }

            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - 80);
            StringBuilder history = new StringBuilder("Recent Audit Entries\n====================\n\n");
            for (int i = from; i < lines.size(); i++) {
                history.append(lines.get(i)).append("\n");
            }
            historyArea.setText(history.toString());
            historyArea.setCaretPosition(0);
        } catch (Exception e) {
            historyArea.setText("Could not load audit history.");
        }
    }

    private String hashText(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "hash-unavailable";
        }
    }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private void openSettingsDialog() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getRootPane().setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        gbc.gridy = 0;

        JTextField outputDirField = new JTextField(preferences.get(PREF_OUTPUT_DIR, DEFAULT_OUTPUT_DIR));
        JTextField labelTemplateField = new JTextField(preferences.get(PREF_LABEL_TEMPLATE, DEFAULT_LABEL_TEMPLATE));
        JTextField signOutTemplateField = new JTextField(preferences.get(PREF_SIGNOUT_TEMPLATE, DEFAULT_SIGNOUT_TEMPLATE));
        JTextField donorHistorySheetField = new JTextField(preferences.get(PREF_DONOR_HISTORY_SHEET_URL, DEFAULT_DONOR_HISTORY_SHEET_URL));
        outputDirField.setColumns(40);
        labelTemplateField.setColumns(40);
        signOutTemplateField.setColumns(40);
        donorHistorySheetField.setColumns(40);

        JButton browseOutput = new JButton("Browse...");
        browseOutput.addActionListener(e -> {
            String selected = chooseDirectory(outputDirField.getText().trim());
            if (selected != null) {
                outputDirField.setText(selected);
            }
        });

        JButton browseLabel = new JButton("Browse...");
        browseLabel.addActionListener(e -> {
            String selected = chooseFile(labelTemplateField.getText().trim(), "Word Template", "docx");
            if (selected != null) {
                labelTemplateField.setText(selected);
            }
        });

        JButton browseSignOut = new JButton("Browse...");
        browseSignOut.addActionListener(e -> {
            String selected = chooseFile(signOutTemplateField.getText().trim(), "Excel Template", "xlsx");
            if (selected != null) {
                signOutTemplateField.setText(selected);
            }
        });

        addSettingsRow(fields, gbc, "Output Directory", outputDirField, browseOutput);
        addSettingsRow(fields, gbc, "Label Template File", labelTemplateField, browseLabel);
        addSettingsRow(fields, gbc, "Sign-Out Template File", signOutTemplateField, browseSignOut);
        addSettingsRow(fields, gbc, "Google Sheet URL", donorHistorySheetField, new JLabel(""));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton setupWizard = new JButton("Run Setup Wizard");
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");

        setupWizard.addActionListener(e -> {
            dialog.dispose();
            SwingUtilities.invokeLater(() -> openSetupWizard(false));
        });
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            saveSettingsValues(
                    outputDirField.getText().trim(),
                    labelTemplateField.getText().trim(),
                    signOutTemplateField.getText().trim(),
                    donorHistorySheetField.getText().trim(),
                    true
            );
            dialog.dispose();
        });

        actions.add(setupWizard);
        actions.add(cancel);
        actions.add(save);

        dialog.add(fields, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openSetupWizard(boolean firstRun) {
        JDialog dialog = new JDialog(this, "HIC Studio Setup Wizard", true);
        dialog.setLayout(new BorderLayout(12, 12));
        dialog.getRootPane().setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel intro = new JLabel(
                "<html><div style='font-family:Avenir Next; font-size:13px; color:#33465A;'>" +
                        "<b>Welcome to HIC Studio.</b><br/>" +
                        "Choose your output folder and template files so exports work out of the box." +
                        "</div></html>"
        );

        JLabel runtimeStatusLabel = new JLabel(runtimeStatusHtml());

        JButton openJdkDownload = new JButton("Download Oracle JDK 17");
        openJdkDownload.addActionListener(e -> openUrl(recommendedJdkUrl()));

        JButton openInstallGuide = new JButton("Open Oracle JDK 17 Page");
        openInstallGuide.addActionListener(e -> openUrl(ORACLE_JDK_17_URL));

        JButton recheckRuntime = new JButton("Re-check Runtime");
        recheckRuntime.addActionListener(e -> runtimeStatusLabel.setText(runtimeStatusHtml()));

        JPanel runtimeActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        runtimeActions.add(openJdkDownload);
        runtimeActions.add(openInstallGuide);
        runtimeActions.add(recheckRuntime);

        JPanel runtimePanel = new JPanel(new BorderLayout(0, 8));
        runtimePanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(206, 219, 234), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        runtimePanel.setBackground(new Color(247, 251, 255));
        runtimePanel.add(runtimeStatusLabel, BorderLayout.CENTER);
        runtimePanel.add(runtimeActions, BorderLayout.SOUTH);

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        gbc.gridy = 0;

        JTextField outputDirField = new JTextField(preferences.get(PREF_OUTPUT_DIR, DEFAULT_OUTPUT_DIR));
        JTextField labelTemplateField = new JTextField(preferences.get(PREF_LABEL_TEMPLATE, DEFAULT_LABEL_TEMPLATE));
        JTextField signOutTemplateField = new JTextField(preferences.get(PREF_SIGNOUT_TEMPLATE, DEFAULT_SIGNOUT_TEMPLATE));
        JTextField donorHistorySheetField = new JTextField(preferences.get(PREF_DONOR_HISTORY_SHEET_URL, DEFAULT_DONOR_HISTORY_SHEET_URL));
        outputDirField.setColumns(42);
        labelTemplateField.setColumns(42);
        signOutTemplateField.setColumns(42);
        donorHistorySheetField.setColumns(42);

        JButton browseOutput = new JButton("Browse...");
        browseOutput.addActionListener(e -> {
            String selected = chooseDirectory(outputDirField.getText().trim());
            if (selected != null) {
                outputDirField.setText(selected);
            }
        });

        JButton browseLabel = new JButton("Browse...");
        browseLabel.addActionListener(e -> {
            String selected = chooseFile(labelTemplateField.getText().trim(), "Word Template", "docx");
            if (selected != null) {
                labelTemplateField.setText(selected);
            }
        });

        JButton browseSignOut = new JButton("Browse...");
        browseSignOut.addActionListener(e -> {
            String selected = chooseFile(signOutTemplateField.getText().trim(), "Excel Template", "xlsx");
            if (selected != null) {
                signOutTemplateField.setText(selected);
            }
        });

        addSettingsRow(fields, gbc, "Output Directory", outputDirField, browseOutput);
        addSettingsRow(fields, gbc, "Label Template File", labelTemplateField, browseLabel);
        addSettingsRow(fields, gbc, "Sign-Out Template File", signOutTemplateField, browseSignOut);
        addSettingsRow(fields, gbc, "Google Sheet URL", donorHistorySheetField, new JLabel(""));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton skip = new JButton(firstRun ? "Skip for Now" : "Cancel");
        JButton finish = new JButton("Save & Finish");

        skip.addActionListener(e -> {
            if (firstRun) {
                preferences.putBoolean(PREF_SETUP_COMPLETE, true);
                setStatus("Setup skipped. You can rerun Setup Wizard from Settings.");
            }
            dialog.dispose();
        });

        finish.addActionListener(e -> {
            saveSettingsValues(
                    outputDirField.getText().trim(),
                    labelTemplateField.getText().trim(),
                    signOutTemplateField.getText().trim(),
                    donorHistorySheetField.getText().trim(),
                    true
            );
            appendOutput("Setup wizard completed.");
            dialog.dispose();
        });

        actions.add(skip);
        actions.add(finish);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.add(intro, BorderLayout.NORTH);
        top.add(runtimePanel, BorderLayout.CENTER);

        dialog.add(top, BorderLayout.NORTH);
        dialog.add(fields, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void saveSettingsValues(String outputDir, String labelTemplate, String signOutTemplate, String donorHistorySheetUrl, boolean markSetupComplete) {
        preferences.put(PREF_OUTPUT_DIR, outputDir == null || outputDir.isBlank() ? DEFAULT_OUTPUT_DIR : outputDir);
        String normalizedLabelTemplate = normalizeConfiguredPath(labelTemplate);
        String normalizedSignOutTemplate = normalizeConfiguredPath(signOutTemplate);
        preferences.put(PREF_LABEL_TEMPLATE, normalizedLabelTemplate.isBlank() ? DEFAULT_LABEL_TEMPLATE : normalizedLabelTemplate);
        preferences.put(PREF_SIGNOUT_TEMPLATE, normalizedSignOutTemplate.isBlank() ? DEFAULT_SIGNOUT_TEMPLATE : normalizedSignOutTemplate);
        preferences.put(PREF_DONOR_HISTORY_SHEET_URL, donorHistorySheetUrl == null ? DEFAULT_DONOR_HISTORY_SHEET_URL : donorHistorySheetUrl);
        if (markSetupComplete) {
            preferences.putBoolean(PREF_SETUP_COMPLETE, true);
        }
        createOutputDirectoryIfNeeded();
        refreshHistoryPanel();
        appendOutput("Settings saved.");
    }

    private void addSettingsRow(JPanel container, GridBagConstraints gbc, String labelText, JTextField field, Component trailingComponent) {
        gbc.gridx = 0;
        gbc.weightx = 0;
        container.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        container.add(field, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        container.add(trailingComponent, gbc);

        gbc.gridy++;
    }

    private String runtimeStatusHtml() {
        int javaMajor = currentJavaMajor();
        String javaVersion = System.getProperty("java.version", "unknown");
        String javaHome = System.getProperty("java.home", "unknown");
        boolean compatible = javaMajor >= REQUIRED_JAVA_MAJOR;

        String statusColor = compatible ? "#0f8a5f" : "#b03a3a";
        String statusText = compatible ? "Ready" : "Needs Update";
        String details = compatible
                ? "Java runtime is compatible."
                : "Install Oracle JDK " + REQUIRED_JAVA_MAJOR + ", then relaunch HIC Studio.";

        return "<html><div style='font-family:Avenir Next; font-size:12px; color:#2F465B;'>"
                + "<b>Runtime Check</b><br/>"
                + "<span style='display:inline-block; margin-top:4px; margin-bottom:6px; font-weight:700; color:" + statusColor + ";'>"
                + statusText + "</span><br/>"
                + "Required: JDK " + REQUIRED_JAVA_MAJOR + "+<br/>"
                + "Detected: " + escapeHtml(javaVersion) + "<br/>"
                + "Java Home: " + escapeHtml(javaHome) + "<br/>"
                + details
                + "</div></html>";
    }

    private int currentJavaMajor() {
        String version = System.getProperty("java.version", "");
        if (version.isBlank()) {
            return 0;
        }
        try {
            String normalized = version.startsWith("1.") ? version.substring(2) : version;
            int dot = normalized.indexOf('.');
            int dash = normalized.indexOf('-');
            int end = normalized.length();
            if (dot >= 0) {
                end = Math.min(end, dot);
            }
            if (dash >= 0) {
                end = Math.min(end, dash);
            }
            return Integer.parseInt(normalized.substring(0, end));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String recommendedJdkUrl() {
        return ORACLE_JDK_17_URL;
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                JOptionPane.showMessageDialog(this, "Open this link in a browser:\n" + url, "Open Link", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open browser.\nUse this link:\n" + url, "Open Link", JOptionPane.WARNING_MESSAGE);
        }
    }

    private String chooseDirectory(String initialPath) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Output Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (!initialPath.isBlank()) {
            chooser.setCurrentDirectory(Path.of(initialPath).toFile());
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            return chooser.getSelectedFile().getAbsolutePath();
        }
        return null;
    }

    private String chooseFile(String initialPath, String description, String extension) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select " + description);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(description + " (*." + extension + ")", extension));

        if (!initialPath.isBlank()) {
            Path initial = Path.of(initialPath);
            if (Files.exists(initial)) {
                chooser.setSelectedFile(initial.toFile());
            } else if (initial.getParent() != null && Files.exists(initial.getParent())) {
                chooser.setCurrentDirectory(initial.getParent().toFile());
            }
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            return chooser.getSelectedFile().getAbsolutePath();
        }
        return null;
    }

    private static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
    }

    private static class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint paint = new GradientPaint(
                    0, 0, new Color(222, 236, 249),
                    getWidth(), getHeight(), new Color(245, 250, 255)
            );
            g2d.setPaint(paint);
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2d.dispose();
        }
    }
}
