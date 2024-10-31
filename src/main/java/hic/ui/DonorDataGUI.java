package hic.ui;

import hic.datamanagement.TXTFileParser;
import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.util.HICData;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class DonorDataGUI extends JFrame {

    private JTextField donorField;
    private JTextArea dataArea;
    private JTextArea outputArea;
    private List<HICData> hicData;
    private HICExcelLogger hicExcelLogger;
    private Processor processor;
    private TXTFileParser txtFileParser;

    public DonorDataGUI(HICExcelLogger hicExcelLogger, Processor processor) {
        this.hicExcelLogger = hicExcelLogger;
        this.processor = processor;
        this.txtFileParser = new TXTFileParser();

        // Frame settings
        setTitle("HIC Tool v3");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set a consistent font and color style
        Font mainFont = new Font("Segoe UI", Font.PLAIN, 14);
        Color buttonColor = new Color(70, 130, 180); // Steel blue color
        Color allActionsButtonColor = new Color(248, 131, 121);  // coral pink
        Color buttonTextColor = Color.WHITE;
        Color blackTextColor = Color.BLACK;

        // Donor Number Panel
        JPanel donorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        donorPanel.setBorder(new EmptyBorder(10, 10, 10, 10)); // Add padding

        JLabel donorLabel = new JLabel("Donor Number:");
        donorLabel.setFont(mainFont);
        donorField = new JTextField(5);
        donorField.setPreferredSize(new Dimension(100, 25)); // Smaller text field for Donor Number
        donorField.setToolTipText("Enter the donor number here");

        donorPanel.add(donorLabel);
        donorPanel.add(donorField);

        // Data Area for HIC Input Data with a border
        JLabel dataLabel = new JLabel("HIC Input Data:");
        dataLabel.setFont(mainFont);
        dataArea = new JTextArea(5, 20);
        dataArea.setLineWrap(true);
        dataArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(dataArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Input Data"));

        // Action Buttons
        JButton summaryButton = new JButton("Get HIC Summary");
        JButton apheresisButton = new JButton("Calculate Apheresis");
        JButton exportUnsortedButton = new JButton("Export Unsorted");
        JButton exportSortedButton = new JButton("Export Sorted");
        JButton labelButton = new JButton("Make Labels");
        JButton signOutButton = new JButton("Export Sign-Out Sheet");
        JButton performAllButton = new JButton("Perform All Actions");
        JButton clearOutputButton = new JButton("Clear Output");

        // Button Actions
        summaryButton.addActionListener(e -> getHICSummary());
        apheresisButton.addActionListener(e -> calculateApheresis());
        exportUnsortedButton.addActionListener(e -> exportToExcelUnsorted());
        exportSortedButton.addActionListener(e -> exportToExcelSorted());
        labelButton.addActionListener(e -> makeLabels());
        signOutButton.addActionListener(e -> exportToSignOutSheet());
        performAllButton.addActionListener(e -> performAllActions());
        clearOutputButton.addActionListener(e -> outputArea.setText("")); // Clears the output area

        // Customize button appearance
        for (JButton button : new JButton[]{summaryButton, apheresisButton, exportUnsortedButton, exportSortedButton, labelButton, signOutButton}) {
            button.setFont(mainFont);
            button.setBackground(buttonColor);
            button.setForeground(buttonTextColor);
            button.setFocusPainted(false); // Remove the focus outline for a cleaner look
        }

        for (JButton button : new JButton[]{performAllButton}) {
            button.setFont(mainFont);
            button.setBackground(allActionsButtonColor);
            button.setForeground(buttonTextColor);
            button.setFocusPainted(false); // Remove the focus outline for a cleaner look
        }

        for (JButton button : new JButton[]{clearOutputButton}) {
            button.setFont(mainFont);
            button.setBackground(buttonTextColor);
            button.setForeground(blackTextColor);
            button.setFocusPainted(false); // Remove the focus outline for a cleaner look
        }

        // Button Panel with a border and grid layout
        JPanel buttonPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        buttonPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        buttonPanel.add(summaryButton);
        buttonPanel.add(apheresisButton);
        buttonPanel.add(exportUnsortedButton);
        buttonPanel.add(exportSortedButton);
        buttonPanel.add(labelButton);
        buttonPanel.add(signOutButton);
        buttonPanel.add(performAllButton);
        buttonPanel.add(clearOutputButton);

        // Output Area with a monospaced font for alignment
        outputArea = new JTextArea(15, 60);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Output"));
        outputScrollPane.setPreferredSize(new Dimension(800, 300));

        // Set up the split pane with button panel on top and output area at the bottom
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buttonPanel, outputScrollPane);
        splitPane.setResizeWeight(0.5); // Adjust this value to allocate space between the two areas
        splitPane.setDividerSize(8);
        splitPane.setDividerLocation(200);

        // Main layout with donor panel at the top, data input in the middle, and split pane at the bottom
        setLayout(new BorderLayout());
        add(donorPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER); // Data input panel in the middle
        add(splitPane, BorderLayout.SOUTH); // Split pane in the center

        // Display the frame
        setVisible(true);
    }

    // Method to update hicData from the GUI input
    private void updateHICDataFromInput() {
        String dataText = dataArea.getText(); // Get data from dataArea
        txtFileParser.parseFromString(dataText); // Parse the text input
        hicData = txtFileParser.getHICData(); // Update hicData from parsed result
    }

    private void getHICSummary() {
        updateHICDataFromInput();
        String maxAndMinRequests = processor.getHICSummaryString(hicData); //get the max and min requests
        outputArea.setText(maxAndMinRequests);
    }

    private void calculateApheresis() {
        updateHICDataFromInput(); // Update hicData from GUI input
        List<Double> maxAndMinRequests = processor.printHICSummary(hicData);
        String apheresisText = processor.getApheresisCalculationString(maxAndMinRequests); //calculate the amount of apheresis needed
        outputArea.append("\n" + apheresisText);
    }

    private void exportToExcelUnsorted() {
        updateHICDataFromInput(); // Update hicData from GUI input
        hicExcelLogger.logHICData(hicData, "Output Files/UnsortedHICList.xlsx", false);
        outputArea.setText("Exported unsorted data to Excel successfully.");
    }

    private void exportToExcelSorted() {
        updateHICDataFromInput(); // Update hicData from GUI input
        processor.sortByCellTypeAndDateTime(hicData);
        hicExcelLogger.logHICData(hicData, "Output Files/SortedHICList.xlsx", true);
        outputArea.setText("Exported sorted data to Excel successfully.");
    }

    private void makeLabels() {
        updateHICDataFromInput(); // Update hicData from GUI input
        String donor = donorField.getText();
        processor.sortByCellTypeAndDateTime(hicData);
        hicExcelLogger.exportToWord(processor.getCD4CD8CellRecords(hicData), "Templates/HIC_Program_Label_Template.docx", "Output Files/CD4CD8_Labels.docx", donor);
        hicExcelLogger.exportToWord(processor.getOtherCellTypeRecords(hicData), "Templates/HIC_Program_Label_Template.docx", "Output Files/OTHERCellTypes_Labels.docx", donor);
        outputArea.setText("Labels created successfully.");
    }

    private void exportToSignOutSheet() {
        updateHICDataFromInput(); // Update hicData from GUI input
        String donor = donorField.getText();
        hicExcelLogger.exportToSignOutSheet(hicData, "Templates/HIC_Signout_Template.xlsx", "Output Files/HIC Sign-out Sheet.xlsx", donor);
        outputArea.setText("Sign-out sheet exported successfully.");
    }

    private void performAllActions() {
        updateHICDataFromInput(); // Update hicData from GUI input
        getHICSummary();
        calculateApheresis();
        exportToExcelUnsorted();
        exportToExcelSorted();
        makeLabels();
        exportToSignOutSheet();
        outputArea.append("\nPerformed all actions successfully.");
    }
}
