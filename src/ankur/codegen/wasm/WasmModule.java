package ankur.codegen.wasm;

import java.util.List;

// The whole compiled module: one exported function holding the Ankur program's statements,
// the locals it needs, and the pool of বাক্য bytes it refers to. Ankur has no functions of its
// own yet, so one is enough.
//
// `locals` and `localComments` are index-aligned: entry i describes local i. WebAssembly
// requires every local to be declared up front at the start of a function, so the generator
// hoists declarations from anywhere in the program into this flat list.
//
// `data` is every বাক্য literal's UTF-8 bytes, concatenated. It is laid into linear memory at
// offset 0, and a string value is carried around as a (pointer, length) pair of i32s -- the
// module has no string type of its own, and no allocator, so literals are all there can be.
public record WasmModule(
        String name,
        List<WasmType> locals,
        List<String> localComments,
        List<Instr> body,
        byte[] data) {

    // The host supplies দেখাও, because a WebAssembly module on its own has no way to print.
    public static final String HOST_MODULE = "অঙ্কুর";
    public static final String PRINT_INT = "দেখাও_পূর্ণ";
    public static final String PRINT_FLOAT = "দেখাও_দশমিক";
    public static final String PRINT_STRING = "দেখাও_বাক্য";
    public static final String ENTRY_POINT = "চালাও";
    // Exported so the host can read a বাক্য's bytes back out of linear memory to print it.
    public static final String MEMORY_EXPORT = "স্মৃতি";

    // Function indices: the three imports are 0, 1 and 2, so the program's own function is 3.
    public static final int PRINT_INT_INDEX = 0;
    public static final int PRINT_FLOAT_INDEX = 1;
    public static final int PRINT_STRING_INDEX = 2;
    public static final int MAIN_INDEX = 3;

    // One 64 KiB page. Nothing is ever allocated at run time, so the string pool is all that
    // has to fit, and a program with more than 64 KiB of literals in it is not this toy's
    // problem yet.
    public static final int MEMORY_PAGES = 1;
}
