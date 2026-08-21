# অঙ্কুর (Ankur)

**An originally-invented Bangla-script programming language and compiler**
Built for CSE-4114 — Compiler Design and Construction Sessional

---

## What is Ankur?

Ankur (অঙ্কুর, "sprout") is a toy programming language whose keywords and
identifiers are written entirely in Bangla script — `শুরু`/`শেষ` for begin
and end, `পূর্ণ`/`দশমিক` for int and float, `যদি`/`নাহলে` for if/else,
`যতক্ষণ` for while, `দেখাও` for print. This repository is its compiler:
a hand-written lexer, parser, semantic analyzer, and Java code generator,
built entirely in Java 21 with no external frameworks — no ANTLR, no
Maven/Gradle, no third-party libraries anywhere, including in the tests.

```
পূর্ণ x = 10;
পূর্ণ y = 20;
দেখাও(x + y);
```

compiles straight to runnable Java, verified end-to-end: real `javac`,
real `java`, real output.

## Status

Every minimum required compiler feature is implemented and tested:

| Feature | Status |
|---|---|
| Two data types (`পূর্ণ` int, `দশমিক` float) with type checking | ✅ |
| Arithmetic with correct operator precedence | ✅ |
| Assignment statements | ✅ |
| `যদি` / `নাহলে` (if / else) | ✅ |
| `যতক্ষণ` (while loop) | ✅ |
| `দেখাও` (print) | ✅ |
| Syntax error recovery | ✅ |
| No runtime crashes (graceful error handling) | ✅ |
| Code generation to Java | ✅ |
| WebAssembly target (optional) | Not implemented |

## Getting started

Requires JDK 21+. No build tool needed — everything runs through three
PowerShell scripts.

```powershell
.\build.ps1                        # compile src/ and test/ into out/
.\run.ps1 examples\hello.ank        # compile a source file, including Java codegen
.\test.ps1                          # build, then run the full test suite
```

`run.ps1` prints the token stream, the parsed syntax tree, and any
lexical/syntax/semantic errors with line:column positions. On a clean
compile it writes `generated/<ClassName>.java` and prints the exact
`javac`/`java` commands to run it.

## Project structure

```
src/ankur/
  Main.java              entry point: lexer -> parser -> semantic analyzer -> codegen
  errors/                 shared error reporting (ErrorReporter, CompileError, Phase)
  lexer/                  TokenType, Token, Lexer
  parser/                 Parser (hand-written recursive descent)
  parser/ast/             sealed Expr/Stmt hierarchies (Java records) + AstPrinter
  semantic/               Type, Symbol, SymbolTable, SemanticAnalyzer
  codegen/                JavaCodeGenerator — Ankur AST to Java source

test/ankur/tests/         dependency-free test harness, one suite per phase

docs/
  grammar.bnf             complete formal grammar (BNF) + Java codegen mapping
  Ankur-Final-Report.md   Pitch + Compiler Design (UML) + Grammar report

examples/                 sample .ank programs, including deliberate error cases
```

Every line is hand-written — no generated or framework code — so any team
member can explain any part of it in a review.

## Try it

```powershell
.\run.ps1 examples\while_loop.ank      # sums 1..10, prints 55
.\run.ps1 examples\type_error.ank      # a caught semantic error, no crash
.\run.ps1 examples\syntax_error.ank    # a caught syntax error, with recovery
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
| 3 | Shajnin Rahman Omi | 0182320012101065 | — |
| 4 | Arman Hassan Rifat | 0182320012101075 | rifatarmanhasan@gmail.com |
| 5 | Md Shahriar Khan | 0182320012101051 | shahriarkhan155@gmail.com |

## Course requirements

See [`CSE-4114_Project_Requirements.pdf`](CSE-4114_Project_Requirements.pdf)
for the full assignment specification.

---

<p align="center">
  <i>অঙ্কুর doesn't ask a learner to stay in Bangla forever — it asks them to start there.</i>
  <br>
  <sub><b>অঙ্কুর</b> · CSE-4114 Compiler Design and Construction Sessional · 2026</sub>
</p>

