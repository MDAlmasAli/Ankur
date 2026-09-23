package ankur.tac;

// One three-address code instruction: at most three operands, one operation, no nesting.
//
// This is the compiler's intermediate representation. Where the AST says "an if whose
// condition is a comparison of a variable with a literal", TAC says "compute the comparison
// into a temporary, then jump somewhere if it is false" -- control flow becomes explicit
// labels and jumps, and every expression is flattened into a sequence of single operations.
// That is the shape every real machine target ultimately wants, which is why compilers put
// this step between the tree and the code generator.
public sealed interface TacInstr {

    // The bracketed kind shown in the report, e.g. [Copy].
    String kind();

    // The instruction itself, e.g. t1 = বয়স >= ১৮.
    String text();

    record Copy(String dest, String source) implements TacInstr {
        public String kind() {
            return "Copy";
        }

        public String text() {
            return dest + " = " + source;
        }
    }

    record BinOp(String dest, String left, String op, String right) implements TacInstr {
        public String kind() {
            return "BinOp";
        }

        public String text() {
            return dest + " = " + left + " " + op + " " + right;
        }
    }

    record UnOp(String dest, String op, String operand) implements TacInstr {
        public String kind() {
            return "UnOp";
        }

        public String text() {
            return dest + " = " + op + operand;
        }
    }

    record IfFalseGoto(String condition, String label) implements TacInstr {
        public String kind() {
            return "IfFalseGoto";
        }

        public String text() {
            return "শর্ত " + condition + " মিথ্যা হলে " + label + "-এ যাও";
        }
    }

    record Goto(String label) implements TacInstr {
        public String kind() {
            return "Goto";
        }

        public String text() {
            return "যাও " + label;
        }
    }

    record Label(String name) implements TacInstr {
        public String kind() {
            return "Label";
        }

        public String text() {
            return name + ":";
        }
    }

    record Print(String operand) implements TacInstr {
        public String kind() {
            return "Print";
        }

        public String text() {
            return "দেখাও " + operand;
        }
    }
}
