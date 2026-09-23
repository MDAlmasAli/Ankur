# অঙ্কুর (Ankur)

**An originally-invented Bangla-script programming language and compiler**
Built for CSE-4114 — Compiler Design and Construction Sessional

---

## What is Ankur?

Ankur (অঙ্কুর, "sprout") is a toy programming language whose keywords and
identifiers are written entirely in Bangla script — `শুরু`/`শেষ` for begin
and end, `পূর্ণ`/`দশমিক`/`বাক্য` for int, float and string, `যদি`/`নাহলে`
for if/else, `যতক্ষণ` for while, `দেখাও` for print. This repository is its
compiler: a hand-written lexer, parser, semantic analyzer, three-address
code generator, and three back ends (Java, Python and WebAssembly), built
entirely in Java 21 with no external frameworks — no ANTLR, no
Maven/Gradle, no third-party libraries anywhere, including in the tests.

```
পূর্ণ x = 10;
পূর্ণ y = 20;
দেখাও(x + y);
```

compiles straight to runnable Java, Python **and** a real binary
WebAssembly module — all three verified end-to-end by the test suite: real
`javac`, real `java`, a real interpreter, a real WebAssembly host, real
output, and the three agree line for line.

## Status

Every minimum required compiler feature is implemented and tested:

| Feature | Status |
|---|---|
| Two data types with type checking (Ankur has three: `পূর্ণ` int, `দশমিক` float, `বাক্য` string) | ✅ |
| Arithmetic with correct operator precedence | ✅ |
| Assignment statements | ✅ |
| `যদি` / `নাহলে` (if / else) | ✅ |
| `যতক্ষণ` (while loop) | ✅ |
| `দেখাও` (print) | ✅ |
| Syntax error recovery | ✅ |
| No runtime crashes (graceful error handling) | ✅ |
| Code generation to Java | ✅ |
| Code generation to Python | ✅ |
| Three-address code (intermediate representation) | ✅ — beyond the requirements |
| WebAssembly target (optional bonus) | ✅ — both `.wat` and a binary `.wasm` |

## Getting started

Requires JDK 21+. No build tool needed — everything runs through three
PowerShell scripts. Python and Node.js are optional: they are only needed
to *run* the Python and WebAssembly output, never to produce it.

```powershell
.\build.ps1                         # compile src/ and test/ into out/
.\run.ps1 examples\hello.ank        # compile a source file to every target
.\test.ps1                          # build, then run the full test suite
```

`run.ps1` prints a numbered source listing and then one banner per phase:
the token stream, the parsed syntax tree, the symbol table, and the
generated target code — plus any lexical/syntax/semantic errors with their
line:column positions.

On a clean compile it writes, into `generated/`:

| File | What it is |
|---|---|
| `<Name>.tac` | the three-address code, the compiler's own intermediate representation |
| `<Name>.java` | the Java target (course requirement 2.1) |
| `<Name>.py` | the Python target (the other language requirement 2.1 allows) |
| `<Name>.wat` | the WebAssembly text format, readable side by side with the source |
| `<Name>.wasm` | a real binary module, encoded by the compiler itself — no `wat2wasm` needed |
| `<Name>.mjs` | the small JavaScript host that supplies `দেখাও` and starts the module |

Pick one back end with `--target=java`, `--target=python` or
`--target=wasm`; the default is `--target=all`. The `.tac` file is written
whatever the target is, since it is an intermediate step rather than
something you asked for. Running the Java output needs only a JDK; Python
needs an interpreter, and WebAssembly needs a host such as Node.js 16+.

## Project structure

```
src/ankur/
  Main.java              the command-line front end: arguments and printing
  Compiler.java          the pipeline itself, returning the phase-by-phase report
  errors/                 shared error reporting (ErrorReporter, CompileError, Phase)
  lexer/                  TokenType, Token, Lexer
  parser/                 Parser (hand-written recursive descent)
  parser/ast/             sealed Expr/Stmt hierarchies (Java records) + AstPrinter
  semantic/               Type, Symbol, SymbolTable, SemanticAnalyzer
  tac/                    TacGenerator/TacInstr — the intermediate representation
  codegen/                JavaCodeGenerator — Ankur AST to Java source
                          PythonCodeGenerator — Ankur AST to Python 3 source
                          WasmCodeGenerator — Ankur AST to a WebAssembly module
                          NodeRunnerGenerator — the JS host for a .wasm module
  codegen/wasm/           Instr/WasmModule (the instruction model), WatWriter
                          (.wat text) and WasmBinaryWriter (binary .wasm)
  report/                 Console — phase banners and Bangla-Indic numerals

test/ankur/tests/         dependency-free test harness, one suite per phase

docs/
  grammar.bnf             complete formal grammar (BNF) + all three codegen mappings
  Ankur-Final-Report.md   Pitch + Compiler Design (UML) + Grammar report

examples/                 sample .ank programs, including deliberate error cases
```

Every line is hand-written — no generated or framework code — so any team
member can explain any part of it in a review.

## Try it

```powershell
.\run.ps1 examples\while_loop.ank      # sums 1..10, prints ৫৫
.\run.ps1 examples\greeting.ank        # বাক্য (string) type plus if/else
.\run.ps1 examples\type_error.ank      # a caught semantic error, no crash
.\run.ps1 examples\syntax_error.ank    # a caught syntax error, with recovery

# then run any of the three targets -- same program, same output
javac -d generated\out generated\WhileLoop.java; java -cp generated\out WhileLoop
python generated\WhileLoop.py
node generated\WhileLoop.mjs
```

## Documentation

- [`docs/grammar.bnf`](docs/grammar.bnf) — the complete formal grammar
- [`docs/Ankur-Final-Report.md`](docs/Ankur-Final-Report.md) — Pitch,
  Compiler Design (with UML diagrams), and Grammar
- [`team-commit-plan.md`](team-commit-plan.md) — internal team
  coordination notes (who pushes what, in what order)

## Team

| # | Name | Student ID | Email |
|---|---|---|---|
| 1 | MD Almas Ali | 0182320012101068 | sy164425@gmail.com |
| 2 | Abidur Rahman Chowdhury | 0182320012101074 | abidc5778@gmail.com |
| 3 | Shajnin Rahman Omi | 0182320012101065 | shajninomi@gmail.com |
| 4 | Arman Hassan Rifat | 0182320012101075 | rifatarmanhasan@gmail.com |
| 5 | Md Shahriar Khan | 0182320012101051 | shahriarkhan155@gmail.com |

---

<p align="center">
  <i>অঙ্কুর doesn't ask a learner to stay in Bangla forever, it asks them to start there.</i>
  <br>
  <sub><b>অঙ্কুর</b> · CSE-4114 Compiler Design and Construction Sessional · 2026</sub>
</p>

