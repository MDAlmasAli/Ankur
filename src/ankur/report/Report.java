package ankur.report;

import java.util.ArrayList;
import java.util.List;

// Accumulates the compiler's phase-by-phase report. It is a drop-in replacement for the plain
// StringBuilder Compiler used to build with -- append() takes anything StringBuilder.append
// would, in whatever order the pipeline calls it -- but it also remembers where each table
// went, as real headers-and-rows data rather than the ASCII text that renders it.
//
// That second copy exists because ASCII column-padding cannot be trusted for Bangla content:
// a Bangla conjunct is several Unicode code points that render as one glyph cluster of
// unpredictable pixel width, in every font, proportional or "monospace" alike -- so padding by
// code-unit count, which is all a fixed-width guess or even a correctly-measured guess can do,
// never lines an ASCII table's columns up on screen. A console still gets the ASCII form (see
// text()), because a real terminal at least forces each code point into a fixed cell and the
// result is legible even if individual glyphs look cramped. A GUI that can lay out a real
// widget should use sections() instead and render each Table as an actual table component,
// which measures each cell's real rendered width and therefore always lines up.
public final class Report {

    private final StringBuilder text = new StringBuilder();
    private final List<ReportSection> sections = new ArrayList<>();
    private int flushedUpTo = 0;

    public Report append(Object value) {
        text.append(value);
        return this;
    }

    // Appends a table as ASCII text (via Console.table) and records it as a Table section.
    // `renderer` is Console.table itself; passed in rather than called directly so Report,
    // which lives in the same package, does not have to duplicate its column-measuring logic.
    void appendTable(String[] headers, List<String[]> rows, java.util.function.Consumer<StringBuilder> renderer) {
        flushPendingText();
        renderer.accept(text);
        sections.add(new ReportSection.Table(headers, List.copyOf(rows)));
        flushedUpTo = text.length();
    }

    private void flushPendingText() {
        if (text.length() > flushedUpTo) {
            sections.add(new ReportSection.Text(text.substring(flushedUpTo)));
        }
    }

    // The full report as flat text -- what a console prints.
    public String text() {
        return text.toString();
    }

    // The report as an ordered sequence of text and table pieces -- what a GUI renders.
    public List<ReportSection> sections() {
        flushPendingText();
        flushedUpTo = text.length();
        return List.copyOf(sections);
    }
}
