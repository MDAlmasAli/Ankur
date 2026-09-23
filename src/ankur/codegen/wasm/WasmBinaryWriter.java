package ankur.codegen.wasm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Encodes a WasmModule as a real binary .wasm file, following the binary format in section 5
// of the WebAssembly core specification.
//
// Emitting the binary directly -- instead of stopping at .wat and shelling out to wat2wasm --
// means the compiler's output runs with nothing installed but a WebAssembly host, exactly as
// the Java target only needs a JVM. It also keeps the project's no-third-party-tools rule.
public final class WasmBinaryWriter {

    private static final byte[] MAGIC = {0x00, 0x61, 0x73, 0x6D}; // the four bytes \0 a s m
    private static final byte[] VERSION = {0x01, 0x00, 0x00, 0x00};

    private static final int SECTION_TYPE = 1;
    private static final int SECTION_IMPORT = 2;
    private static final int SECTION_FUNCTION = 3;
    private static final int SECTION_MEMORY = 5;
    private static final int SECTION_EXPORT = 7;
    private static final int SECTION_CODE = 10;
    private static final int SECTION_DATA = 11;

    private static final byte FUNC_TYPE = 0x60;
    private static final byte KIND_FUNC = 0x00;
    private static final byte KIND_MEMORY = 0x02;
    private static final byte LIMITS_MIN_ONLY = 0x00;
    private static final byte EMPTY_BLOCK_TYPE = 0x40; // a block that leaves nothing on the stack
    private static final byte END = 0x0B;

    private WasmBinaryWriter() {
    }

    public static byte[] write(WasmModule module) {
        Buf out = new Buf();
        out.raw(MAGIC);
        out.raw(VERSION);

        // Type section: (i32)->() for দেখাও_পূর্ণ, (f64)->() for দেখাও_দশমিক,
        // (i32,i32)->() for দেখাও_বাক্য (a pointer and a length), and ()->() for the program.
        Buf types = new Buf();
        types.u32(4);
        types.byteValue(FUNC_TYPE);
        types.u32(1);
        types.byteValue(WasmType.I32.valType);
        types.u32(0);
        types.byteValue(FUNC_TYPE);
        types.u32(1);
        types.byteValue(WasmType.F64.valType);
        types.u32(0);
        types.byteValue(FUNC_TYPE);
        types.u32(2);
        types.byteValue(WasmType.I32.valType);
        types.byteValue(WasmType.I32.valType);
        types.u32(0);
        types.byteValue(FUNC_TYPE);
        types.u32(0);
        types.u32(0);
        section(out, SECTION_TYPE, types);

        // Import section: the three host printing functions, one per Ankur type.
        Buf imports = new Buf();
        imports.u32(3);
        imports.name(WasmModule.HOST_MODULE);
        imports.name(WasmModule.PRINT_INT);
        imports.byteValue(KIND_FUNC);
        imports.u32(0);
        imports.name(WasmModule.HOST_MODULE);
        imports.name(WasmModule.PRINT_FLOAT);
        imports.byteValue(KIND_FUNC);
        imports.u32(1);
        imports.name(WasmModule.HOST_MODULE);
        imports.name(WasmModule.PRINT_STRING);
        imports.byteValue(KIND_FUNC);
        imports.u32(2);
        section(out, SECTION_IMPORT, imports);

        // Function section: one function, using type index 3.
        Buf functions = new Buf();
        functions.u32(1);
        functions.u32(3);
        section(out, SECTION_FUNCTION, functions);

        // Memory section: one page, holding the বাক্য literals.
        Buf memory = new Buf();
        memory.u32(1);
        memory.byteValue(LIMITS_MIN_ONLY);
        memory.u32(WasmModule.MEMORY_PAGES);
        section(out, SECTION_MEMORY, memory);

        // Export section: the entry point the host calls, and the memory it reads strings from.
        Buf exports = new Buf();
        exports.u32(2);
        exports.name(WasmModule.ENTRY_POINT);
        exports.byteValue(KIND_FUNC);
        exports.u32(WasmModule.MAIN_INDEX);
        exports.name(WasmModule.MEMORY_EXPORT);
        exports.byteValue(KIND_MEMORY);
        exports.u32(0);
        section(out, SECTION_EXPORT, exports);

        // Code section: the function body, length-prefixed.
        Buf body = new Buf();
        body.u32(module.locals().size());
        for (WasmType local : module.locals()) {
            body.u32(1); // a run of one local of this type
            body.byteValue(local.valType);
        }
        writeAll(module.body(), body);
        body.byteValue(END);

        Buf code = new Buf();
        code.u32(1);
        code.u32(body.size());
        code.raw(body.toByteArray());
        section(out, SECTION_CODE, code);

        // Data section: the বাক্য pool, copied into linear memory at offset 0 when the module
        // is instantiated. Omitted entirely when the program uses no strings.
        if (module.data().length > 0) {
            Buf data = new Buf();
            data.u32(1);
            data.u32(0); // active segment in memory 0
            data.byteValue((byte) 0x41); // i32.const
            data.s32(0);                 // ... 0: the offset to copy the bytes to
            data.byteValue(END);
            data.u32(module.data().length);
            data.raw(module.data());
            section(out, SECTION_DATA, data);
        }

        return out.toByteArray();
    }

    private static void section(Buf out, int id, Buf contents) {
        out.byteValue((byte) id);
        out.u32(contents.size());
        out.raw(contents.toByteArray());
    }

    private static void writeAll(List<Instr> instructions, Buf out) {
        for (Instr instr : instructions) {
            write(instr, out);
        }
    }

    private static void write(Instr instr, Buf out) {
        switch (instr) {
            case Instr.Op op -> out.byteValue((byte) op.opcode());
            case Instr.ConstI32 c -> {
                out.byteValue((byte) 0x41);
                out.s32(c.value());
            }
            case Instr.ConstF64 c -> {
                out.byteValue((byte) 0x44);
                out.f64(c.value());
            }
            case Instr.LocalGet g -> {
                out.byteValue((byte) 0x20);
                out.u32(g.index());
            }
            case Instr.LocalSet s -> {
                out.byteValue((byte) 0x21);
                out.u32(s.index());
            }
            case Instr.Call c -> {
                out.byteValue((byte) 0x10);
                out.u32(c.funcIndex());
            }
            case Instr.Br b -> {
                out.byteValue((byte) 0x0C);
                out.u32(b.depth());
            }
            case Instr.BrIf b -> {
                out.byteValue((byte) 0x0D);
                out.u32(b.depth());
            }
            case Instr.Block b -> {
                out.byteValue((byte) 0x02);
                out.byteValue(EMPTY_BLOCK_TYPE);
                writeAll(b.body(), out);
                out.byteValue(END);
            }
            case Instr.Loop l -> {
                out.byteValue((byte) 0x03);
                out.byteValue(EMPTY_BLOCK_TYPE);
                writeAll(l.body(), out);
                out.byteValue(END);
            }
            case Instr.If i -> {
                out.byteValue((byte) 0x04);
                out.byteValue(i.result() == null ? EMPTY_BLOCK_TYPE : i.result().valType);
                writeAll(i.thenBody(), out);
                if (i.elseBody() != null) {
                    out.byteValue((byte) 0x05);
                    writeAll(i.elseBody(), out);
                }
                out.byteValue(END);
            }
        }
    }

    // A growable byte buffer that knows the handful of encodings the binary format uses.
    private static final class Buf {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        void byteValue(byte b) {
            bytes.write(b);
        }

        void raw(byte[] data) {
            bytes.writeBytes(data);
        }

        // Unsigned LEB128: seven bits per byte, low group first, high bit set while more follow.
        void u32(int value) {
            int remaining = value;
            do {
                int group = remaining & 0x7F;
                remaining >>>= 7;
                bytes.write(remaining != 0 ? (group | 0x80) : group);
            } while (remaining != 0);
        }

        // Signed LEB128, used by i32.const so that negative literals encode correctly.
        void s32(int value) {
            int remaining = value;
            while (true) {
                int group = remaining & 0x7F;
                remaining >>= 7;
                boolean signBitSet = (group & 0x40) != 0;
                if ((remaining == 0 && !signBitSet) || (remaining == -1 && signBitSet)) {
                    bytes.write(group);
                    return;
                }
                bytes.write(group | 0x80);
            }
        }

        // f64.const carries the raw IEEE-754 bits, little-endian.
        void f64(double value) {
            long bits = Double.doubleToLongBits(value);
            for (int i = 0; i < 8; i++) {
                bytes.write((int) ((bits >>> (8 * i)) & 0xFF));
            }
        }

        // A name is a length-prefixed UTF-8 byte string -- so Bangla import and export names
        // are legal here, unlike in .wat identifiers.
        void name(String text) {
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            u32(encoded.length);
            raw(encoded);
        }

        int size() {
            return bytes.size();
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }
}
