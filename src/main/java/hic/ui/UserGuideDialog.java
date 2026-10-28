package hic.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserGuideDialog extends JDialog {
    private static final String WINDOW_KEY = "hic.userGuide";

    private UserGuideDialog(JFrame owner) throws IOException {
        super(owner, "HIC Studio — User Guide", false);
        setContentPane(new GuidePanel(loadTopics()));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1160, screen.width - 80), Math.min(800, screen.height - 100));
        setLocationRelativeTo(owner);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeGuide");
        getRootPane().getActionMap().put("closeGuide", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) { dispose(); }
        });
    }

    public static void open(JFrame owner) {
        Object existing = owner.getRootPane().getClientProperty(WINDOW_KEY);
        if (existing instanceof UserGuideDialog guide && guide.isDisplayable()) {
            guide.setVisible(true);
            guide.toFront();
            return;
        }
        try {
            UserGuideDialog guide = new UserGuideDialog(owner);
            owner.getRootPane().putClientProperty(WINDOW_KEY, guide);
            guide.setVisible(true);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(owner, "Could not open the bundled user guide.\n" + exception.getMessage(),
                    "HIC Studio User Guide", JOptionPane.ERROR_MESSAGE);
        }
    }

    static List<Topic> loadTopics() throws IOException {
        try (var input = UserGuideDialog.class.getResourceAsStream("/hic-user-guide.html")) {
            if (input == null) throw new IOException("The user guide resource is missing. Reinstall the application.");
            String html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher sections = Pattern.compile("(?s)<h2 id=\"([a-z0-9-]+)\">([^<]+)</h2>(.*?)(?=<h2 id=|</body>)").matcher(html);
            List<Topic> topics = new ArrayList<>();
            while (sections.find()) {
                String section = "<h2>" + sections.group(2) + "</h2>" + sections.group(3);
                HTMLEditorKit kit = new HTMLEditorKit();
                var document = kit.createDefaultDocument();
                try {
                    kit.read(new StringReader(section), document, 0);
                    topics.add(new Topic(sections.group(1), sections.group(2), section,
                            document.getText(0, document.getLength()).toLowerCase(Locale.ROOT)));
                } catch (BadLocationException exception) {
                    throw new IOException("Could not read user guide topic " + sections.group(1), exception);
                }
            }
            if (topics.isEmpty()) throw new IOException("No indexed topics were found in the user guide.");
            return List.copyOf(topics);
        }
    }

    record Topic(String id, String title, String html, String searchableText) {
        @Override
        public String toString() { return title; }
    }

    static final class GuidePanel extends JPanel {
        private final List<Topic> topics;
        private final JTextField search = new JTextField();
        private final DefaultListModel<Topic> index = new DefaultListModel<>();
        private final JList<Topic> topicList = new JList<>(index) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                int row = locationToIndex(event.getPoint());
                return row < 0 ? null : getModel().getElementAt(row).title();
            }
        };
        private final JEditorPane reader = new JEditorPane("text/html", "");
        private final JLabel matches = new JLabel();
        private final JLabel position = new JLabel();
        private final JButton previous = new JButton("← Previous Topic");
        private final JButton next = new JButton("Next Topic →");
        private Topic current;
        private boolean updatingIndex;

        GuidePanel(List<Topic> topics) {
            super(new BorderLayout(12, 12));
            this.topics = topics;
            setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            setBackground(new Color(244, 248, 252));
            JPanel searchBar = new JPanel(new BorderLayout(10, 6));
            searchBar.setOpaque(false);
            JLabel heading = new JLabel("HIC Studio User Guide");
            heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            searchBar.add(heading, BorderLayout.NORTH);
            search.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            search.setToolTipText("Search all instructions, not just topic titles. Try saved cancellations, unique IDs, or not isolating.");
            search.getAccessibleContext().setAccessibleName("Search user guide");
            JPanel query = new JPanel(new BorderLayout(8, 0));
            query.setOpaque(false);
            query.add(new JLabel("Search instructions:"), BorderLayout.WEST);
            query.add(search, BorderLayout.CENTER);
            JButton clear = new JButton("Clear Search");
            clear.addActionListener(event -> search.setText(""));
            query.add(clear, BorderLayout.EAST);
            searchBar.add(query, BorderLayout.CENTER);
            searchBar.add(matches, BorderLayout.SOUTH);
            add(searchBar, BorderLayout.NORTH);

            topicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            topicList.setFixedCellHeight(30);
            topicList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            topicList.setToolTipText("Select a topic to read its instructions.");
            topicList.getAccessibleContext().setAccessibleName("User guide topic index");
            JScrollPane contents = new JScrollPane(topicList);
            contents.setBorder(BorderFactory.createTitledBorder("Topic Index"));
            contents.setMinimumSize(new Dimension(210, 200));
            reader.setEditable(false);
            reader.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
            reader.getAccessibleContext().setAccessibleName("User guide instructions");
            JSplitPane pages = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contents, new JScrollPane(reader));
            pages.setDividerLocation(290);
            pages.setResizeWeight(0.25);
            pages.setContinuousLayout(true);
            add(pages, BorderLayout.CENTER);

            JPanel navigation = new JPanel(new BorderLayout(10, 0));
            navigation.setOpaque(false);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            controls.setOpaque(false);
            controls.add(previous);
            controls.add(next);
            controls.add(position);
            navigation.add(controls, BorderLayout.WEST);
            JButton close = new JButton("Close Guide");
            close.addActionListener(event -> {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window != null) window.dispose();
            });
            navigation.add(close, BorderLayout.EAST);
            add(navigation, BorderLayout.SOUTH);

            topicList.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting() && !updatingIndex && topicList.getSelectedValue() != null) showTopic(topicList.getSelectedValue());
            });
            reader.addHyperlinkListener(event -> {
                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().startsWith("#")) {
                    navigate(event.getDescription().substring(1));
                }
            });
            previous.addActionListener(event -> navigate(topics.get(topics.indexOf(current) - 1).id()));
            next.addActionListener(event -> navigate(topics.get(topics.indexOf(current) + 1).id()));
            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) { filterTopics(); }
                @Override
                public void removeUpdate(DocumentEvent event) { filterTopics(); }
                @Override
                public void changedUpdate(DocumentEvent event) { filterTopics(); }
            });
            filterTopics();
        }

        private String[] searchTerms() {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            return query.isEmpty() ? new String[0] : query.split("\\s+");
        }

        private void filterTopics() {
            updatingIndex = true;
            index.clear();
            for (Topic topic : topics) {
                if (java.util.Arrays.stream(searchTerms()).allMatch(topic.searchableText()::contains)) index.addElement(topic);
            }
            Topic selected = current != null && index.contains(current) ? current : index.isEmpty() ? null : index.get(0);
            topicList.setSelectedValue(selected, true);
            updatingIndex = false;
            matches.setText(index.size() + " of " + topics.size() + " topics • Search matches are highlighted • All instructions are available offline");
            if (selected != null) {
                showTopic(selected);
            } else {
                current = null;
                reader.setText("<html><body><h2>No matching topics</h2><p>Try fewer words, a button name, or Clear Search to return to the full topic index.</p></body></html>");
                previous.setEnabled(false);
                next.setEnabled(false);
                position.setText("No results");
            }
        }

        private void navigate(String id) {
            for (Topic topic : topics) {
                if (!topic.id().equals(id)) continue;
                if (!index.contains(topic)) search.setText("");
                topicList.setSelectedValue(topic, true);
                return;
            }
        }

        private void showTopic(Topic topic) {
            current = topic;
            reader.setText("<html><head><style>body {font-family:sans-serif;font-size:14pt;color:#223448;} "
                    + "h2 {color:#14589e;} h3 {color:#28556d;} p,li {margin-bottom:9px;} "
                    + "table {border-collapse:collapse;} td,th {padding:7px;border:1px solid #cedaea;} "
                    + "th {background-color:#e7f0fa;} a {color:#14589e;}</style></head><body>" + topic.html() + "</body></html>");
            reader.getHighlighter().removeAllHighlights();
            int firstMatch = -1;
            try {
                String visible = reader.getDocument().getText(0, reader.getDocument().getLength()).toLowerCase(Locale.ROOT);
                for (String term : searchTerms()) {
                    for (int start = visible.indexOf(term); start >= 0; start = visible.indexOf(term, start + term.length())) {
                        reader.getHighlighter().addHighlight(start, start + term.length(),
                                new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 235, 153)));
                        if (firstMatch < 0 || start < firstMatch) firstMatch = start;
                    }
                }
            } catch (BadLocationException exception) {
                reader.getHighlighter().removeAllHighlights();
            }
            reader.setCaretPosition(Math.max(0, firstMatch));
            int chapter = topics.indexOf(topic);
            previous.setEnabled(chapter > 0);
            next.setEnabled(chapter < topics.size() - 1);
            position.setText("Topic " + (chapter + 1) + " / " + topics.size());
            SwingUtilities.invokeLater(() -> {
                if (topicList.getSelectedValue() == topic) topicList.ensureIndexIsVisible(topicList.getSelectedIndex());
            });
        }
    }
}
