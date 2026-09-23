package ankur.codegen;

import ankur.codegen.wasm.Instr;
import ankur.codegen.wasm.WasmModule;
import ankur.codegen.wasm.WasmType;
import ankur.parser.ast.AssignStmt;
import ankur.parser.ast.BinaryExpr;
import ankur.parser.ast.BinaryOp;
import ankur.parser.ast.BlockStmt;
import ankur.parser.ast.Expr;
import ankur.parser.ast.IdentifierExpr;
import ankur.parser.ast.IfStmt;
import ankur.parser.ast.NumberLiteral;
import ankur.parser.ast.PrintStmt;
import ankur.parser.ast.Program;
import ankur.parser.ast.Stmt;
import ankur.parser.ast.StringLiteral;
import ankur.parser.ast.UnaryExpr;
import ankur.parser.ast.VarDeclStmt;
import ankur.parser.ast.WhileStmt;
import ankur.semantic.Type;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Translates a semantically-valid Ankur AST into a WebAssembly module (the optional target of
// course requirement 2.2). Like JavaCodeGenerator it assumes SemanticAnalyzer already passed,
// and does no validation of its own -- but unlike it, it needs the analyzer's expression types
// as well as its AST, because WebAssembly is typed and offers no implicit conversion.
//
// Four things make this harder than emitting Java, and they are what most of this class is:
//
//   1. WebAssembly is a stack machine, so expressions come out in post-order: operands are
//      pushed first and the operator instruction consumes them.
//   2. No implicit numeric conversion whatsoever. Java lets `int + double` widen silently;
//      here every পূর্ণ value used where a দশমিক is expected needs an explicit
//      f64.convert_i32_s, inserted from the analyzer's recorded types.
//   3. All locals must be declared at the top of a function, so declarations from anywhere in
//      the program are hoisted into one flat list and Ankur's block scoping is resolved here,
//      at compile time, by giving every declaration its own local index (which is also what
//      makes shadowing work).
//   4. There is no string type. A বাক্য literal's UTF-8 bytes go into the module's data
//      segment, and the value itself is a (pointer, length) pair of i32s -- so a বাক্য
//      variable occupies two locals, not one.
public final class WasmCodeGenerator {

    private final Map<Expr, Type> types;

    private final Deque<Map<String, Integer>> scopes = new ArrayDeque<>();
    private final List<WasmType> localTypes = new ArrayList<>();
    private final List<String> localComments = new ArrayList<>();

    // Every বাক্য literal in the program, concatenated. Identical literals share one entry.
    private final ByteArrayOutputStream stringPool = new ByteArrayOutputStream();
    private final Map<String, int[]> internedStrings = new HashMap<>(); // text -> {offset, length}

    // Scratch locals for the দশমিক `%` workaround (see genFloatRemainder). One pair per level
    // of nesting, so a remainder inside a remainder does not clobber the outer one's operands.
    private final List<int[]> remainderScratch = new ArrayList<>();
    private int remainderDepth = 0;

    // `expressionTypes` comes from SemanticAnalyzer.expressionTypes().
    public WasmCodeGenerator(Map<Expr, Type> expressionTypes) {
        this.types = expressionTypes;
    }

    public WasmModule generate(Program program, String moduleName) {
        scopes.push(new HashMap<>());
        List<Instr> body = new ArrayList<>();
        for (Stmt stmt : program.statements()) {
            genStmt(stmt, body);
        }
        scopes.pop();
        return new WasmModule(moduleName, List.copyOf(localTypes), List.copyOf(localComments),
                List.copyOf(body), stringPool.toByteArray());
    }

    // ---- Statements ----

    private void genStmt(Stmt stmt, List<Instr> out) {
        switch (stmt) {
            case VarDeclStmt v -> genVarDecl(v, out);
            case AssignStmt a -> {
                int index = resolve(a.name());
                genValue(a.value(), typeOfLocal(index), out);
                store(index, typeOfLocal(index), a.name(), out);
            }
            case PrintStmt p -> genPrint(p, out);
            case IfStmt i -> {
                genValue(i.condition(), Type.BOOLEAN, out);
                List<Instr> thenBody = new ArrayList<>();
                genBlockBody(i.thenBranch(), thenBody);
                List<Instr> elseBody = null;
                if (i.elseBranch() != null) {
                    elseBody = new ArrayList<>();
                    genBlockBody(i.elseBranch(), elseBody);
                }
                out.add(new Instr.If(null, thenBody, elseBody));
            }
            case WhileStmt w -> genWhile(w, out);
            case BlockStmt b -> genBlockBody(b, out);
        }
    }

    private void genVarDecl(VarDeclStmt v, List<Instr> out) {
        Type type = switch (v.type()) {
            case PURNO -> Type.INT;
            case DOSHOMIK -> Type.FLOAT;
            case BAKKO -> Type.STRING;
        };

        // The initializer is generated before the variable is declared, so that a nested
        // declaration shadowing an outer name (পূর্ণ x = x + 1;) still reads the outer one --
        // matching the order SemanticAnalyzer.analyzeVarDecl checks it in.
        if (v.initializer() != null) {
            genValue(v.initializer(), type, out);
        } else {
            pushDefault(type, out);
        }

        // WebAssembly locals are function-scoped and zeroed once, at function entry. A
        // declaration inside a loop body must still take its initial value on every
        // iteration, so the store below is emitted unconditionally rather than relying on
        // that default.
        int index = declare(v.name(), type);
        store(index, type, v.name(), out);
    }

    private void genPrint(PrintStmt p, List<Instr> out) {
        Type type = ankurType(p.value());
        genValue(p.value(), type, out);
        // A WebAssembly module cannot print on its own, so দেখাও becomes a call into the host,
        // picking the import that matches the argument's type. The বাক্য one takes two
        // arguments, the pointer and the length already on the stack.
        out.add(switch (type) {
            case STRING -> new Instr.Call(WasmModule.PRINT_STRING_INDEX, WasmModule.PRINT_STRING);
            case FLOAT -> new Instr.Call(WasmModule.PRINT_FLOAT_INDEX, WasmModule.PRINT_FLOAT);
            default -> new Instr.Call(WasmModule.PRINT_INT_INDEX, WasmModule.PRINT_INT);
        });
    }

    // WebAssembly has no while loop. The standard shape is a `block` (whose branch target is
    // its end, giving "break") wrapped around a `loop` (whose branch target is its start,
    // giving "continue"): test the condition, branch out of the block when it is false, run
    // the body, then branch back to the top of the loop.
    private void genWhile(WhileStmt w, List<Instr> out) {
        List<Instr> loopBody = new ArrayList<>();
        genValue(w.condition(), Type.BOOLEAN, loopBody);
        loopBody.add(Instr.I32_EQZ);
        loopBody.add(new Instr.BrIf(1)); // 1 = past the end of the enclosing block, i.e. exit
        genBlockBody(w.body(), loopBody);
        loopBody.add(new Instr.Br(0));  // 0 = back to the top of the loop
        out.add(new Instr.Block(List.of(new Instr.Loop(loopBody))));
    }

    private void genBlockBody(BlockStmt block, List<Instr> out) {
        scopes.push(new HashMap<>());
        for (Stmt stmt : block.statements()) {
            genStmt(stmt, out);
        }
        scopes.pop();
    }

    // ---- Expressions ----

    // Generates `expr` and leaves a value of type `expected` on the stack: one i32 or f64 for
    // a number or a boolean, and a pointer followed by a length for a বাক্য.
    private void genValue(Expr expr, Type expected, List<Instr> out) {
        if (expected == Type.STRING) {
            genString(expr, out);
            return;
        }
        WasmType target = wasmTypeOf(expected);
        // A পূর্ণ literal in a দশমিক position is folded straight to an f64 constant instead of
        // being pushed as an i32 and converted; same value, one instruction less to read.
        if (target == WasmType.F64 && expr instanceof NumberLiteral n && !n.isFloat()) {
            out.add(new Instr.ConstF64(n.intValue()));
            return;
        }
        genExpr(expr, out);
        if (target == WasmType.F64 && wasmTypeOf(ankurType(expr)) == WasmType.I32) {
            out.add(Instr.F64_CONVERT_I32_S);
        }
    }

    // Pushes a বাক্য as its (pointer, length) pair. Only a literal or a variable can be one:
    // there are no string operators in the language, precisely because producing a new string
    // at run time would need an allocator this module does not have.
    private void genString(Expr expr, List<Instr> out) {
        switch (expr) {
            case StringLiteral s -> {
                int[] location = intern(s.value());
                out.add(new Instr.ConstI32(location[0]));
                out.add(new Instr.ConstI32(location[1]));
            }
            case IdentifierExpr id -> {
                int pointer = resolve(id.name());
                out.add(new Instr.LocalGet(pointer, id.name() + " (pointer)"));
                out.add(new Instr.LocalGet(pointer + 1, id.name() + " (length)"));
            }
            default -> throw new IllegalStateException(
                    "Not a বাক্য expression during code generation: " + expr
                            + " (the program should have been validated by SemanticAnalyzer first)");
        }
    }

    private void genExpr(Expr expr, List<Instr> out) {
        switch (expr) {
            case NumberLiteral n -> out.add(n.isFloat()
                    ? new Instr.ConstF64(n.floatValue())
                    : new Instr.ConstI32((int) n.intValue()));
            case StringLiteral s -> genString(s, out);
            case IdentifierExpr id -> out.add(new Instr.LocalGet(resolve(id.name()), id.name()));
            case UnaryExpr u -> genUnary(u, out);
            case BinaryExpr b -> genBinary(b, out);
        }
    }

    private void genUnary(UnaryExpr u, List<Instr> out) {
        switch (u.op()) {
            case POS -> genExpr(u.operand(), out); // unary + is a no-op on both numeric types
            case NOT -> {
                genValue(u.operand(), Type.BOOLEAN, out);
                out.add(Instr.I32_EQZ); // a boolean is an i32 of 0 or 1, so "is zero" is "not"
            }
            case NEG -> {
                Type operandType = ankurType(u.operand());
                if (operandType == Type.FLOAT) {
                    genValue(u.operand(), Type.FLOAT, out);
                    out.add(Instr.F64_NEG);
                } else {
                    // There is no i32.neg instruction; negation is a subtraction from zero.
                    out.add(new Instr.ConstI32(0));
                    genValue(u.operand(), Type.INT, out);
                    out.add(Instr.I32_SUB);
                }
            }
        }
    }

    private void genBinary(BinaryExpr b, List<Instr> out) {
        switch (b.op()) {
            case ADD, SUB, MUL, DIV, MOD -> {
                Type type = ankurType(b);
                if (b.op() == BinaryOp.MOD && type == Type.FLOAT) {
                    genFloatRemainder(b, out);
                    return;
                }
                genValue(b.left(), type, out);
                genValue(b.right(), type, out);
                out.add(arithmeticOp(b.op(), wasmTypeOf(type)));
            }
            case EQ, NEQ, LT, GT, LE, GE -> {
                // Both sides must reach the comparison as the same type, so a mixed
                // পূর্ণ/দশমিক comparison widens the পূর্ণ side first.
                Type operandType = (ankurType(b.left()) == Type.FLOAT || ankurType(b.right()) == Type.FLOAT)
                        ? Type.FLOAT
                        : Type.INT;
                genValue(b.left(), operandType, out);
                genValue(b.right(), operandType, out);
                out.add(comparisonOp(b.op(), wasmTypeOf(operandType)));
            }
            // i32.and / i32.or would evaluate both sides, but Ankur's && and || short-circuit
            // like Java's. That is observable: in `x != 0 && 10 / x > 0` the right-hand side
            // would trap on an integer division by zero. A structured `if` producing an i32 is
            // how WebAssembly expresses the short-circuiting version.
            case AND -> {
                genValue(b.left(), Type.BOOLEAN, out);
                List<Instr> right = new ArrayList<>();
                genValue(b.right(), Type.BOOLEAN, right);
                out.add(new Instr.If(WasmType.I32, right, List.of(new Instr.ConstI32(0))));
            }
            case OR -> {
                genValue(b.left(), Type.BOOLEAN, out);
                List<Instr> right = new ArrayList<>();
                genValue(b.right(), Type.BOOLEAN, right);
                out.add(new Instr.If(WasmType.I32, List.of(new Instr.ConstI32(1)), right));
            }
        }
    }

    // WebAssembly has i32.rem_s but no f64 remainder instruction at all, so দশমিক % is
    // computed the way IEEE-754 truncated remainder is defined: a - trunc(a / b) * b. Both
    // operands are needed twice, and a stack machine cannot re-read a value it has consumed,
    // so they are parked in scratch locals first.
    private void genFloatRemainder(BinaryExpr b, List<Instr> out) {
        int depth = remainderDepth++;
        try {
            int[] scratch = scratchPair(depth);
            genValue(b.left(), Type.FLOAT, out);
            out.add(new Instr.LocalSet(scratch[0], "দশমিক % left operand"));
            genValue(b.right(), Type.FLOAT, out);
            out.add(new Instr.LocalSet(scratch[1], "দশমিক % right operand"));

            out.add(new Instr.LocalGet(scratch[0], null));
            out.add(new Instr.LocalGet(scratch[0], null));
            out.add(new Instr.LocalGet(scratch[1], null));
            out.add(Instr.F64_DIV);
            out.add(Instr.F64_TRUNC);
            out.add(new Instr.LocalGet(scratch[1], null));
            out.add(Instr.F64_MUL);
            out.add(Instr.F64_SUB);
        } finally {
            remainderDepth--;
        }
    }

    private int[] scratchPair(int depth) {
        while (remainderScratch.size() <= depth) {
            int left = addLocal(WasmType.F64, "scratch for দশমিক %");
            int right = addLocal(WasmType.F64, "scratch for দশমিক %");
            remainderScratch.add(new int[]{left, right});
        }
        return remainderScratch.get(depth);
    }

    // ---- The বাক্য pool ----

    // Adds a literal's UTF-8 bytes to the data segment and returns {offset, length}. Repeating
    // the same literal reuses the bytes already there rather than appending a second copy.
    private int[] intern(String value) {
        int[] existing = internedStrings.get(value);
        if (existing != null) {
            return existing;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int[] location = {stringPool.size(), bytes.length};
        stringPool.writeBytes(bytes);
        internedStrings.put(value, location);
        return location;
    }

    // ---- Types ----

    private Type ankurType(Expr expr) {
        Type type = types.get(expr);
        if (type == null) {
            throw new IllegalStateException(
                    "No recorded type for " + expr + " during code generation "
                            + "(the program should have been validated by SemanticAnalyzer first)");
        }
        return type;
    }

    // BOOLEAN has no WebAssembly type of its own: a condition is an i32 of 0 or 1, which is
    // exactly how WebAssembly's own comparison instructions leave their result.
    private static WasmType wasmTypeOf(Type type) {
        return type == Type.FLOAT ? WasmType.F64 : WasmType.I32;
    }

    private void pushDefault(Type type, List<Instr> out) {
        switch (type) {
            case FLOAT -> out.add(new Instr.ConstF64(0.0));
            // An uninitialised বাক্য is the empty string: a pointer of 0 with a length of 0,
            // which reads no bytes at all and so is safe whatever the pool contains.
            case STRING -> {
                out.add(new Instr.ConstI32(0));
                out.add(new Instr.ConstI32(0));
            }
            default -> out.add(new Instr.ConstI32(0));
        }
    }

    // ---- Locals and scopes ----

    // A বাক্য occupies two consecutive locals, the pointer then the length, so storing one
    // pops the length first: it was pushed last and is therefore on top of the stack.
    private void store(int index, Type type, String name, List<Instr> out) {
        if (type == Type.STRING) {
            out.add(new Instr.LocalSet(index + 1, name + " (length)"));
            out.add(new Instr.LocalSet(index, name + " (pointer)"));
        } else {
            out.add(new Instr.LocalSet(index, name));
        }
    }

    private int declare(String ankurName, Type type) {
        int index;
        if (type == Type.STRING) {
            index = addLocal(WasmType.I32, ankurName + " (pointer)");
            addLocal(WasmType.I32, ankurName + " (length)");
        } else {
            index = addLocal(wasmTypeOf(type), ankurName);
        }
        scopes.peek().put(ankurName, index);
        return index;
    }

    private int addLocal(WasmType type, String comment) {
        localTypes.add(type);
        localComments.add(comment);
        return localTypes.size() - 1;
    }

    private Type typeOfLocal(int index) {
        // A local whose comment marks it as a pointer is the first half of a বাক্য pair.
        String comment = localComments.get(index);
        if (comment != null && comment.endsWith(" (pointer)")) {
            return Type.STRING;
        }
        return localTypes.get(index) == WasmType.F64 ? Type.FLOAT : Type.INT;
    }

    private int resolve(String ankurName) {
        for (Map<String, Integer> scope : scopes) {
            Integer found = scope.get(ankurName);
            if (found != null) {
                return found;
            }
        }
        throw new IllegalStateException(
                "Unresolved identifier '" + ankurName + "' during code generation "
                        + "(the program should have been validated by SemanticAnalyzer first)");
    }

    private static Instr.Op arithmeticOp(BinaryOp op, WasmType type) {
        boolean isInt = type == WasmType.I32;
        return switch (op) {
            case ADD -> isInt ? Instr.I32_ADD : Instr.F64_ADD;
            case SUB -> isInt ? Instr.I32_SUB : Instr.F64_SUB;
            case MUL -> isInt ? Instr.I32_MUL : Instr.F64_MUL;
            case DIV -> isInt ? Instr.I32_DIV_S : Instr.F64_DIV;
            case MOD -> Instr.I32_REM_S; // the f64 case never reaches here (see genBinary)
            default -> throw new IllegalStateException("not an arithmetic operator: " + op);
        };
    }

    private static Instr.Op comparisonOp(BinaryOp op, WasmType type) {
        boolean isInt = type == WasmType.I32;
        return switch (op) {
            case EQ -> isInt ? Instr.I32_EQ : Instr.F64_EQ;
            case NEQ -> isInt ? Instr.I32_NE : Instr.F64_NE;
            // The _s suffix is "signed": WebAssembly integers carry no signedness of their
            // own, the comparison instruction chooses the interpretation. পূর্ণ is signed.
            case LT -> isInt ? Instr.I32_LT_S : Instr.F64_LT;
            case GT -> isInt ? Instr.I32_GT_S : Instr.F64_GT;
            case LE -> isInt ? Instr.I32_LE_S : Instr.F64_LE;
            case GE -> isInt ? Instr.I32_GE_S : Instr.F64_GE;
            default -> throw new IllegalStateException("not a comparison operator: " + op);
        };
    }
}
