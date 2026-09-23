package tools;

import ankur.Compiler;
// Imported explicitly because java.io.* below also has a Console, and the single-type
// import is what makes this one win.
import ankur.report.Console;
import ankur.report.Report;
import ankur.report.ReportSection;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A small Swing GUI around the existing Ankur compiler pipeline, purely for live demos:
 * write Ankur source on the left, click "চালাও (Run)", see the full pipeline report plus the
 * generated Java program's actual output on the right -- without typing commands in a terminal.
 *
 * The report itself comes from ankur.Compiler, the same call ankur.Main makes, so this window
 * shows exactly what the console shows. This is a demo convenience only; it does not change or
 * replace anything in the graded pipeline.
 */
public final class AnkurPlayground {

    private static final String CLASS_NAME = "AnkurPlaygroundProgram";
    private static final String[] KEYWORDS = {
            "শুরু", "শেষ", "যদি", "নাহলে", "যতক্ষণ", "দেখাও", "পূর্ণ", "দশমিক", "বাক্য"
    };

    private record Result(Report report, boolean success) {
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        // Cross-platform default "Metal" L&F looks dated (flat, no shading); Nimbus is bundled
        // in every JDK and gives buttons/combo-boxes/splitters a modern look for free.
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // Fall back to the default L&F -- purely cosmetic, never worth failing the demo over.
        }
        SwingUtilities.invokeLater(AnkurPlayground::createAndShowGui);
    }

    private static void createAndShowGui() {
        Font banglaFont = new Font("Nirmala UI", Font.PLAIN, 16);

        // Swing's default L&F fonts don't cover Bangla glyphs, so borders/labels/buttons/
        // combo-boxes show tofu boxes unless every UI-manager font is overridden up front.
        UIManager.put("Label.font", banglaFont);
        UIManager.put("Button.font", banglaFont.deriveFont(Font.BOLD));
        UIManager.put("TitledBorder.font", banglaFont);
        UIManager.put("ComboBox.font", banglaFont);
        UIManager.put("TextArea.font", banglaFont);

        JFrame frame = new JFrame("অঙ্কুর Playground — Live Compiler Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 750);
        frame.setLocationRelativeTo(null);
        frame.setIconImage(createIcon());
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        JTextPane codeArea = new JTextPane();
        codeArea.setFont(banglaFont);
        codeArea.setText(loadExample("hello.ank"));
        codeArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> highlight(codeArea));
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> highlight(codeArea));
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Fired by our own highlight() call (attribute-only change) -- ignoring this
                // is what keeps highlight() from re-triggering itself forever.
            }
        });
        JScrollPane codeScroll = new JScrollPane(codeArea);
        codeScroll.setBorder(BorderFactory.createTitledBorder("অঙ্কুর সোর্স কোড (Ankur source code)"));

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(banglaFont.deriveFont(Font.BOLD, 18f));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(0x1e1e1e));
        statusLabel.setForeground(new Color(0xd4d4d4));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JTextPane outputArea = new JTextPane();
        outputArea.setFont(banglaFont);
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(0x1e1e1e));
        outputArea.setForeground(new Color(0xd4d4d4));
        outputArea.setCaretColor(Color.WHITE);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("আউটপুট (Compile + Run output)"));

        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.add(statusLabel, BorderLayout.NORTH);
        outputPanel.add(outputScroll, BorderLayout.CENTER);

        String[] examples = {"hello.ank", "greeting.ank", "if_else.ank", "while_loop.ank",
                "type_error.ank", "syntax_error.ank"};
        JComboBox<String> exampleBox = new JComboBox<>(examples);
        exampleBox.addActionListener(e -> {
            codeArea.setText(loadExample((String) exampleBox.getSelectedItem()));
            highlight(codeArea);
        });

        JButton clearButton = new JButton("খালি করো (Clear)");
        clearButton.setFont(banglaFont.deriveFont(Font.BOLD, 16f));
        clearButton.addActionListener(e -> codeArea.setText("শুরু\n    \nশেষ\n"));

        JButton runButton = new JButton("চালাও (Run)");
        runButton.setFont(banglaFont.deriveFont(Font.BOLD, 16f));
        runButton.setBackground(new Color(0x2e7d32));
        runButton.setForeground(Color.WHITE);
        runButton.addActionListener(e -> {
            runButton.setEnabled(false);
            outputArea.setText("চলছে... (running)\n");
            statusLabel.setText(" চলছে... (running)");
            statusLabel.setForeground(new Color(0xd4d4d4));
            String source = codeArea.getText();
            new SwingWorker<Result, Void>() {
                @Override
                protected Result doInBackground() {
                    return compileAndRun(source);
                }

                @Override
                protected void done() {
                    try {
                        Result result = get();
                        renderReport(outputArea, result.report());
                        highlightOutput(outputArea);
                        outputArea.setCaretPosition(0);
                        if (result.success()) {
                            statusLabel.setText(" ✓ সফলভাবে কম্পাইল ও রান হয়েছে (compiled and ran successfully)");
                            statusLabel.setForeground(new Color(0x4caf50));
                        } else {
                            statusLabel.setText(" ✗ ত্রুটি পাওয়া গেছে (error found)");
                            statusLabel.setForeground(new Color(0xef5350));
                        }
                    } catch (Exception ex) {
                        outputArea.setText("Internal error: " + ex);
                        statusLabel.setText(" ✗ ত্রুটি (error)");
                        statusLabel.setForeground(new Color(0xef5350));
                    }
                    runButton.setEnabled(true);
                }
            }.execute();
        });

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(new JLabel("Example:"));
        topBar.add(exampleBox);
        topBar.add(clearButton);
        topBar.add(runButton);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(topBar, BorderLayout.NORTH);
        leftPanel.add(codeScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, outputPanel);
        splitPane.setResizeWeight(0.5);

        frame.add(splitPane);
        frame.setVisible(true);
        highlight(codeArea);
    }

    // Draws a simple round "অ" badge at runtime instead of shipping an image asset.
    private static Image createIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x2e7d32));
        g.fillOval(0, 0, 64, 64);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Nirmala UI", Font.BOLD, 40));
        FontMetrics fm = g.getFontMetrics();
        String text = "অ";
        int x = (64 - fm.stringWidth(text)) / 2;
        int y = (64 - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
        g.dispose();
        return img;
    }

    // Minimal syntax highlighting: Ankur keywords in bold blue, "// ..." comments in green
    // italic, everything else left as the plain-text default.
    private static void highlight(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }

        SimpleAttributeSet plain = new SimpleAttributeSet();
        StyleConstants.setForeground(plain, Color.BLACK);
        StyleConstants.setBold(plain, false);
        StyleConstants.setItalic(plain, false);
        doc.setCharacterAttributes(0, text.length(), plain, true);

        // Four clearly distinct hues (blue / orange / teal / green) so keyword, number,
        // identifier, and comment are each unmistakable at a glance -- not four shades that
        // all read as "bluish" from a few feet away on a projector.
        SimpleAttributeSet keyword = new SimpleAttributeSet();
        StyleConstants.setForeground(keyword, new Color(0x0000FF));
        StyleConstants.setBold(keyword, true);

        SimpleAttributeSet number = new SimpleAttributeSet();
        StyleConstants.setForeground(number, new Color(0xE07B00));
        StyleConstants.setBold(number, true);

        SimpleAttributeSet identifier = new SimpleAttributeSet();
        StyleConstants.setForeground(identifier, new Color(0x267F99));

        // One pass over every "word" (Bangla or ASCII letters/marks/digits) in the text,
        // classifying each as a keyword, a number, or a variable/identifier -- same three-way
        // split a real IDE like VS Code makes.
        java.util.regex.Matcher tokenMatcher =
                java.util.regex.Pattern.compile("[\\p{L}\\p{M}\\p{Nd}_]+").matcher(text);
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group();
            int start = tokenMatcher.start();
            SimpleAttributeSet style;
            if (isKeyword(token)) {
                style = keyword;
            } else if (isNumber(token)) {
                style = number;
            } else {
                style = identifier;
            }
            doc.setCharacterAttributes(start, token.length(), style, false);
        }

        SimpleAttributeSet comment = new SimpleAttributeSet();
        StyleConstants.setForeground(comment, new Color(0x008000));
        StyleConstants.setItalic(comment, true);
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            int commentAt = text.indexOf("//", lineStart);
            if (commentAt >= 0 && commentAt < lineEnd) {
                doc.setCharacterAttributes(commentAt, lineEnd - commentAt, comment, false);
            }
            lineStart = lineEnd + 1;
        }
    }

    // AST/pipeline node labels that AstPrinter and Main's phase headers use -- coloring these
    // the same as the code editor's keyword/number/identifier palette makes the output pane
    // read like a real IDE's structured output instead of one flat wall of gray text.
    // The node labels AstPrinter emits, highlighted wherever they appear in the report.
    private static final String[] NODE_LABELS = {
            "PROGRAM", "DECLARATION", "ASSIGNMENT", "PRINT", "IF", "ELSE", "WHILE", "BLOCK",
            "THEN", "BODY", "CONDITION", "TYPE", "NAME", "VALUE"
    };

    // Replaces the output pane's content with the report, section by section: plain text goes
    // in as text, and a table goes in as a real embedded JTable instead of the ASCII form
    // Report.text() would give a console. That is not cosmetic -- ASCII column padding cannot
    // be trusted for Bangla content in any font, proportional or "monospace" alike (a conjunct
    // is several Unicode code points rendering as one glyph cluster of unpredictable pixel
    // width), so it is the only way these two tables end up genuinely aligned on screen. See
    // Report's own comment for the full explanation.
    private static void renderReport(JTextPane pane, Report report) {
        pane.setText("");
        StyledDocument doc = pane.getStyledDocument();
        for (ReportSection section : report.sections()) {
            try {
                switch (section) {
                    case ReportSection.Text t -> doc.insertString(doc.getLength(), t.content(), null);
                    case ReportSection.Table t -> {
                        pane.setCaretPosition(doc.getLength());
                        pane.insertComponent(buildTableComponent(t.headers(), t.rows()));
                        doc.insertString(doc.getLength(), "\n", null);
                    }
                }
            } catch (BadLocationException ignored) {
                // Never actually thrown here: every insertion point is doc.getLength(), the
                // one offset that is always valid.
            }
        }
    }

    // A dark-themed, read-only JTable sized from the font's own measured glyph widths --
    // exactly what makes this align correctly where character-count-based ASCII padding
    // cannot (see renderReport's comment).
    private static JComponent buildTableComponent(String[] headers, List<String[]> rows) {
        Font tableFont = new Font("Nirmala UI", Font.PLAIN, 15);
        Color background = new Color(0x252526);
        Color foreground = new Color(0xd4d4d4);
        Color headerBackground = new Color(0x333333);
        Color headerForeground = new Color(0x4FC1FF);
        Color gridColor = new Color(0x3c3c3c);

        Object[][] data = new Object[rows.size()][];
        for (int r = 0; r < rows.size(); r++) {
            data[r] = rows.get(r);
        }
        DefaultTableModel model = new DefaultTableModel(data, headers) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setFont(tableFont);
        table.setBackground(background);
        table.setForeground(foreground);
        table.setGridColor(gridColor);
        table.setSelectionBackground(background);
        table.setSelectionForeground(foreground);
        table.setRowSelectionAllowed(false);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.getTableHeader().setFont(tableFont.deriveFont(Font.BOLD));
        table.getTableHeader().setBackground(headerBackground);
        table.getTableHeader().setForeground(headerForeground);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);

        // The default row height assumes Latin ascent/descent; Bangla matras and conjuncts run
        // taller and get clipped without this.
        FontMetrics metrics = table.getFontMetrics(tableFont);
        table.setRowHeight(metrics.getHeight() + 10);

        // Column widths measured from the font's actual rendered width of every cell in that
        // column -- real glyph shaping, not an assumption that one code unit is one fixed cell.
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer();
        cellRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int c = 0; c < headers.length; c++) {
            int widest = metrics.stringWidth(headers[c]);
            for (String[] row : rows) {
                widest = Math.max(widest, metrics.stringWidth(row[c]));
            }
            table.getColumnModel().getColumn(c).setPreferredWidth(widest + 24);
            table.getColumnModel().getColumn(c).setCellRenderer(cellRenderer);
        }
        table.setPreferredScrollableViewportSize(table.getPreferredSize());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(background);
        wrapper.setBorder(BorderFactory.createLineBorder(gridColor));
        wrapper.add(table.getTableHeader(), BorderLayout.NORTH);
        wrapper.add(table, BorderLayout.CENTER);
        // Sized to exactly its content, like everything else in this report -- not stretched
        // to fill the whole text pane width.
        wrapper.setMaximumSize(wrapper.getPreferredSize());
        return wrapper;
    }

    private static void highlightOutput(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }

        SimpleAttributeSet plain = new SimpleAttributeSet();
        StyleConstants.setForeground(plain, new Color(0xd4d4d4));
        // `replace=false`: this document can hold an embedded table component now (see
        // renderReport), and a component run's real attribute is its ComponentAttribute, not
        // a color. `replace=true` would overwrite that attribute wholesale and turn the table
        // back into a stray placeholder character. Merging the foreground instead leaves it be.
        doc.setCharacterAttributes(0, text.length(), plain, false);

        SimpleAttributeSet header = new SimpleAttributeSet();
        StyleConstants.setForeground(header, new Color(0x4FC1FF));
        StyleConstants.setBold(header, true);

        SimpleAttributeSet node = new SimpleAttributeSet();
        StyleConstants.setForeground(node, new Color(0x569CD6));
        StyleConstants.setBold(node, true);

        SimpleAttributeSet enumValue = new SimpleAttributeSet();
        StyleConstants.setForeground(enumValue, new Color(0x4EC9B0));

        SimpleAttributeSet number = new SimpleAttributeSet();
        StyleConstants.setForeground(number, new Color(0xE0A050));
        StyleConstants.setBold(number, true);

        SimpleAttributeSet errorLine = new SimpleAttributeSet();
        StyleConstants.setForeground(errorLine, new Color(0xF14C4C));
        StyleConstants.setBold(errorLine, true);

        SimpleAttributeSet successLine = new SimpleAttributeSet();
        StyleConstants.setForeground(successLine, new Color(0x6A9955));

        for (String label : NODE_LABELS) {
            int idx = 0;
            while ((idx = text.indexOf(label, idx)) >= 0) {
                doc.setCharacterAttributes(idx, label.length(), node, false);
                idx += label.length();
            }
        }

        java.util.regex.Matcher tokenMatcher =
                java.util.regex.Pattern.compile("[\\p{L}\\p{M}\\p{Nd}_]+").matcher(text);
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group();
            if (isNumber(token)) {
                doc.setCharacterAttributes(tokenMatcher.start(), token.length(), number, false);
            } else if (token.equals(token.toUpperCase()) && token.length() > 1
                    && !Character.isDigit(token.charAt(0))) {
                // ALL-CAPS tokens are enum values printed by AstPrinter (PURNO, ADD, MUL, ...).
                doc.setCharacterAttributes(tokenMatcher.start(), token.length(), enumValue, false);
            }
        }

        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String line = text.substring(lineStart, lineEnd);
            if (line.startsWith("==") || line.startsWith("--") || line.startsWith("PHASE ")
                    || line.equals("SOURCE CODE (উৎস কোড)") || line.equals("FULL PIPELINE RESULT")) {
                doc.setCharacterAttributes(lineStart, line.length(), header, false);
            } else if (line.contains("Status: FAILED") || line.toLowerCase().contains("error")
                    || line.contains("failed") || line.contains("Fatal")) {
                doc.setCharacterAttributes(lineStart, line.length(), errorLine, false);
            } else if (line.contains("Status: OK") || line.startsWith("Compilation successful")
                    || line.startsWith("javac: (compiled cleanly)")) {
                doc.setCharacterAttributes(lineStart, line.length(), successLine, false);
            }
            lineStart = lineEnd + 1;
        }
    }

    private static boolean isKeyword(String token) {
        for (String kw : KEYWORDS) {
            if (kw.equals(token)) {
                return true;
            }
        }
        return false;
    }

    // A number token is made up entirely of digit characters (Bangla ０-９ or ASCII 0-9);
    // the "." between an integer and fractional part isn't a word character, so it stays
    // outside this token and keeps the default color -- visually seamless either way.
    private static boolean isNumber(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.digit(token.charAt(i), 10) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String loadExample(String fileName) {
        try {
            return Files.readString(Path.of("examples", fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "// could not load examples/" + fileName;
        }
    }

    // Compiles through ankur.Compiler -- the very same call ankur.Main makes, so this window
    // shows the identical phase-by-phase report -- and then, on a clean compile, actually
    // invokes javac/java on the generated file and appends the real program output as a
    // further phase before the closing verdict.
    private static Result compileAndRun(String source) {
        Path outDir = Path.of("generated");
        Compiler.Result compiled = Compiler.compile(source, CLASS_NAME, Compiler.Target.ALL, outDir);
        Report report = compiled.report();

        if (!compiled.success()) {
            Console.verdict(report, false);
            return new Result(report, false);
        }

        try {
            String javaHome = System.getProperty("java.home");
            Path javac = Path.of(javaHome, "bin", "javac.exe");
            Path javaExe = Path.of(javaHome, "bin", "java.exe");
            Path classesDir = outDir.resolve("out");
            Files.createDirectories(classesDir);

            Console.banner(report, 6, "RUN THE GENERATED JAVA", "জেনারেট করা জাভা চালানো");
            ProcessResult compile = runProcess(new ProcessBuilder(
                    javac.toString(), "-encoding", "UTF-8", "-d", classesDir.toString(),
                    compiled.javaFile().toString()));
            report.append(compile.output.isBlank() ? "javac: (compiled cleanly)\n" : compile.output);
            if (compile.exitCode != 0) {
                Console.status(report, "Run", false);
                Console.verdict(report, false);
                return new Result(report, false);
            }

            report.append("\nprogram output (প্রোগ্রামের আউটপুট):\n");
            // Force the child JVM's stdout/stderr to UTF-8. Without this it encodes with the
            // Windows console codepage (e.g. Cp1252), which cannot represent Bangla-Indic
            // digits, so every printed digit comes back as '?'.
            ProcessResult run = runProcess(new ProcessBuilder(
                    javaExe.toString(),
                    "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dfile.encoding=UTF-8",
                    "-cp", classesDir.toString(), CLASS_NAME));
            report.append(run.output);
            boolean ranCleanly = run.exitCode == 0;
            Console.status(report, "Run", ranCleanly);
            Console.verdict(report, ranCleanly);
            return new Result(report, ranCleanly);
        } catch (IOException | InterruptedException ex) {
            report.append("Failed to compile/run generated Java: ").append(ex).append('\n');
            Console.status(report, "Run", false);
            Console.verdict(report, false);
            return new Result(report, false);
        }
    }

    private record ProcessResult(String output, int exitCode) {
    }

    private static ProcessResult runProcess(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ProcessResult(output, exitCode);
    }

}
