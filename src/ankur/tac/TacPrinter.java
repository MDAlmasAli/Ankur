package ankur.tac;

import ankur.report.Console;

import java.util.List;

// Renders a three-address code listing as a numbered table, and as the text written to the
// .tac file. Both come from the same method so the file and the console agree.
public final class TacPrinter {

    private TacPrinter() {
    }

    public static String print(List<TacInstr> instructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(Console.padRight("নং", 6)).append(Console.padRight("ধরন", 16))
                .append("নির্দেশ\n");
        sb.append("  ").append("-".repeat(Console.WIDTH - 4)).append('\n');
        if (instructions.isEmpty()) {
            sb.append("  (কোনো নির্দেশ নেই)\n");
            return sb.toString();
        }
        for (int i = 0; i < instructions.size(); i++) {
            TacInstr instruction = instructions.get(i);
            String number = Console.bn(i + 1) + ".";
            sb.append("  ").append(Console.padRight(number, 6))
                    .append(Console.padRight("[" + instruction.kind() + "]", 16))
                    .append(instruction.text()).append('\n');
        }
        return sb.toString();
    }
}
