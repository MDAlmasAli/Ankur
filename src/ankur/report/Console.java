package ankur.report;

import java.util.List;

// Small formatting helpers for the compiler's phase-by-phase report: the banners, rules and
// Bangla-Indic numerals that make each stage of the pipeline readable at a glance during a
// live demo. Nothing here affects compilation -- it is presentation only.
//
// Everything appends to a Report rather than printing, so the same report can go to the
// console (ankur.Main) or into a window (the playground GUI) without being written twice. See
// Report's own comment for why a table needs more than text to render correctly everywhere.
public final class Console {

    public static final int WIDTH = 60;

    private Console() {
    }

    // A phase banner, e.g.
    // ============================================================
    // PHASE 1: LEXICAL ANALYSIS (লেক্সিক্যাল অ্যানালাইসিস)
    // ============================================================
    public static void banner(Report report, int phaseNumber, String englishTitle, String banglaTitle) {
        doubleRule(report);
        report.append("PHASE ").append(phaseNumber).append(": ")
                .append(englishTitle).append(" (").append(banglaTitle).append(")\n");
        doubleRule(report);
    }

    public static void heading(Report report, String title) {
        report.append(title).append('\n');
        rule(report);
    }

    public static void rule(Report report) {
        report.append("-".repeat(WIDTH)).append('\n');
    }

    public static void doubleRule(Report report) {
        report.append("=".repeat(WIDTH)).append('\n');
    }

    public static void status(Report report, String label, boolean ok) {
        report.append(label).append(" Status: ").append(ok ? "OK ✓" : "FAILED ✗").append('\n');
    }

    // The closing block of a run. It is appended by whoever owns the last phase -- the console
    // front end after code generation, the playground after it has also run the program -- so
    // that it always comes last, whatever the caller added in between.
    public static void verdict(Report report, boolean success) {
        report.append('\n');
        doubleRule(report);
        report.append("FULL PIPELINE RESULT\n");
        doubleRule(report);
        report.append(success
                ? "Compilation successful: no lexical, syntax, or semantic errors found. ✓\n"
                : "Compilation stopped. Fix the error(s) above and run again. ✗\n");
    }

    // Ankur accepts Bangla-Indic and ASCII digits interchangeably in source (see Lexer), and
    // the generated programs print their numbers back in Bangla-Indic. The compiler's own
    // report follows the same rule so that line numbers and counts do not switch scripts.
    public static String bn(long value) {
        return bn(String.valueOf(value));
    }

    public static String bn(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('০' + (c - '0')) : c);
        }
        return out.toString();
    }

    public static String padRight(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }

    // Appends a table both as ASCII text (for a console) and as a Table section on the Report
    // (for a GUI to render as a real widget instead -- see Report's comment for why that
    // matters for Bangla content specifically). The ASCII column widths are still measured
    // from the actual cells rather than guessed: Bangla identifiers vary a lot in length
    // (যোগফল vs দ্বিতীয়_সংখ্যা), so a fixed guess either collides with the separator or
    // leaves a huge ragged gap, row to row, even before the font-rendering problem above.
    public static void table(Report report, String[] headers, List<String[]> rows) {
        report.appendTable(headers, rows, sb -> renderAsciiTable(sb, headers, rows));
    }

    private static void renderAsciiTable(StringBuilder sb, String[] headers, List<String[]> rows) {
        int columns = headers.length;
        int[] width = new int[columns];
        for (int c = 0; c < columns; c++) {
            width[c] = headers[c].length();
        }
        for (String[] row : rows) {
            for (int c = 0; c < columns; c++) {
                width[c] = Math.max(width[c], row[c].length());
            }
        }

        tableRow(sb, headers, width);
        for (int c = 0; c < columns; c++) {
            sb.append("-".repeat(width[c]));
            sb.append(c < columns - 1 ? "-+-" : "");
        }
        sb.append('\n');
        for (String[] row : rows) {
            tableRow(sb, row, width);
        }
    }

    private static void tableRow(StringBuilder sb, String[] cells, int[] width) {
        for (int c = 0; c < cells.length; c++) {
            sb.append(padRight(cells[c], width[c]));
            sb.append(c < cells.length - 1 ? " | " : "\n");
        }
    }
}
