package ankur.codegen.wasm;

// The only two WebAssembly value types Ankur needs: পূর্ণ maps to i32 and দশমিক to f64.
// BOOLEAN has no WebAssembly type of its own -- comparisons and logical operators leave an
// i32 of 0 or 1 on the stack, which is exactly how WebAssembly itself models conditions.
public enum WasmType {
    I32("i32", (byte) 0x7F),
    F64("f64", (byte) 0x7C);

    public final String watName;
    public final byte valType; // the byte this type is encoded as in a binary .wasm module

    WasmType(String watName, byte valType) {
        this.watName = watName;
        this.valType = valType;
    }
}
