package ankur.codegen.wasm;

import java.util.List;

// One WebAssembly instruction, kept as data rather than as text.
//
// WasmCodeGenerator walks the Ankur AST once and produces a list of these; WatWriter then
// renders them as readable .wat text and WasmBinaryWriter encodes the very same list as a
// binary .wasm module. Keeping a single instruction list behind both writers is what stops
// the human-readable output and the executable output from ever drifting apart.
public sealed interface Instr {

    // An instruction with no immediate operands: every arithmetic, comparison and conversion
    // opcode. The mnemonic is for .wat, the opcode byte is for .wasm.
    record Op(String mnemonic, int opcode) implements Instr {
    }

    record ConstI32(int value) implements Instr {
    }

    record ConstF64(double value) implements Instr {
    }

    // `comment` carries the original Ankur name, which cannot be used as a .wat identifier
    // (the .wat identifier grammar is ASCII-only, so Bangla names are illegal there).
    record LocalGet(int index, String comment) implements Instr {
    }

    record LocalSet(int index, String comment) implements Instr {
    }

    record Call(int funcIndex, String name) implements Instr {
    }

    // A structured `if`. `result` is null for a statement-level যদি that leaves nothing on the
    // stack, and I32 for the short-circuiting && / || forms, which produce a boolean.
    // `elseBody` is null when there is no নাহলে part.
    record If(WasmType result, List<Instr> thenBody, List<Instr> elseBody) implements Instr {
    }

    record Block(List<Instr> body) implements Instr {
    }

    record Loop(List<Instr> body) implements Instr {
    }

    // Branch to the enclosing structure `depth` levels out (0 = the innermost one). A `br` to
    // a `loop` jumps back to its start; a `br` to a `block` jumps forward past its end.
    record Br(int depth) implements Instr {
    }

    record BrIf(int depth) implements Instr {
    }

    // ---- Opcode table (WebAssembly core specification, section 5.4) ----

    Op I32_ADD = new Op("i32.add", 0x6A);
    Op I32_SUB = new Op("i32.sub", 0x6B);
    Op I32_MUL = new Op("i32.mul", 0x6C);
    Op I32_DIV_S = new Op("i32.div_s", 0x6D);
    Op I32_REM_S = new Op("i32.rem_s", 0x6F);
    Op I32_EQZ = new Op("i32.eqz", 0x45);
    Op I32_EQ = new Op("i32.eq", 0x46);
    Op I32_NE = new Op("i32.ne", 0x47);
    Op I32_LT_S = new Op("i32.lt_s", 0x48);
    Op I32_GT_S = new Op("i32.gt_s", 0x4A);
    Op I32_LE_S = new Op("i32.le_s", 0x4C);
    Op I32_GE_S = new Op("i32.ge_s", 0x4E);

    Op F64_ADD = new Op("f64.add", 0xA0);
    Op F64_SUB = new Op("f64.sub", 0xA1);
    Op F64_MUL = new Op("f64.mul", 0xA2);
    Op F64_DIV = new Op("f64.div", 0xA3);
    Op F64_NEG = new Op("f64.neg", 0x9A);
    Op F64_TRUNC = new Op("f64.trunc", 0x9D);
    Op F64_EQ = new Op("f64.eq", 0x61);
    Op F64_NE = new Op("f64.ne", 0x62);
    Op F64_LT = new Op("f64.lt", 0x63);
    Op F64_GT = new Op("f64.gt", 0x64);
    Op F64_LE = new Op("f64.le", 0x65);
    Op F64_GE = new Op("f64.ge", 0x66);

    // Widening পূর্ণ -> দশমিক. WebAssembly has no implicit numeric promotion at all, so the
    // generator has to insert this instruction everywhere Ankur's type rules allow widening.
    Op F64_CONVERT_I32_S = new Op("f64.convert_i32_s", 0xB7);
}
