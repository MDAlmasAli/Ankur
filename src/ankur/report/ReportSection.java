package ankur.report;

import java.util.List;

// One piece of the compiler's report: either plain text, or a table with real column/row data
// attached. A console just prints a table as aligned ASCII text (see Console.table); a GUI that
// can lay out real widgets should render it as one instead -- see the note on Report for why
// ASCII alignment cannot be trusted for Bangla content in the first place.
public sealed interface ReportSection {

    record Text(String content) implements ReportSection {
    }

    record Table(String[] headers, List<String[]> rows) implements ReportSection {
    }
}
