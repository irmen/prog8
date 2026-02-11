# Qwen Assistant Memory for prog8 Project

This file contains project-specific information that helps the Qwen assistant understand and work effectively with the prog8 codebase.

## Project Overview
- This project is a compiler for the Prog8 programming language.
- Prog8 is a programming language primarily targeting 8-bit retro systems with the 6502 CPU, such as the Commodore 64, Commodore 128, and Commander X16.
- The compiler has a 6502 code generator backend, and an IR code generator.
- The IR code is meant to be used in a new machine specific code generator backend (primarily 6502 but maybe 68000 as well later)
- The compiler includes a simple 'virtual machine' that can execute the IR code directly via interpretation.
- Prog8 source files have .p8 extension
- Prog8 source files are a "module" that can contain 1 ore more "blocks". They can also import other modules, from internal library files or from source files on the filesystem.
- The prog8 compiler is written mostly in Kotlin, those files have the .kt extension.

## Prog8 language feature hints
- an overview of the language features can be found in the documentation file docs/source/introduction.rst
- the syntax and grammar is specified in an ANTLR4 grammar file found in the parser directory
- the syntax for boolean logical operators is 'and', 'or', 'xor', 'not'. Bitwise operators are '&', '|', '^', '~', and '<<','>>' for bit shifting left and right respectively. All operators are documents in docs/source/programming.rst
- note that Prog8 does have a 4-byte signed long type and 5-byte "Microsoft" floating point support
- module imports are done using "%import modulename"

## Module Descriptions
- `beanshell` - EXPERIMENTAL/UNFINISHED Contains BeanShell integration for scripting capabilities within the compiler
- `benchmark-c` - C implementations for performance comparison and benchmarking
- `benchmark-program` - Benchmark programs to test compiler output performance
- `codeCore` - Core code generation utilities and shared components
- `codeGenCpu6502` - 6502 CPU-specific code generator backend (generates assembly for 6502-based systems)
- `codeGenExperimental` - Experimental code generators that are under development
- `codeGenIntermediate` - Intermediate representation (IR) code generator
- `codeGenVirtual` - Virtual machine code generator backend
- `codeOptimizers` - Optimization passes for improving generated code efficiency
- `compiler` - Main compiler executable and top-level compiler logic
- `compilerAst` - complicated Abstract Syntax Tree (AST) where most optimizations also run on, is later transformed into the simpleAst
- `docs` - Documentation files for the Prog8 language and compiler
- `examples` - Example Prog8 programs demonstrating language features
- `intermediate` - Components related to the intermediate representation (IR) of Prog8 programs
- `languageServer` - Language Server Protocol implementation for IDE integration
- `parser` - ANTLR4 Parser implementation for the Prog8 language syntax
- `scripts` - Utility scripts for development, testing, and deployment
- `simpleAst` - Simplified AST that is used to run the code generator backends from
- `syntax-files` - Syntax definition files for editors and IDEs
- `virtualmachine` - Virtual machine implementation that can execute IR code

## Key Information
- never read the files and directories that are ignored via the .aiignore and .gitignore files
- Current development focus areas: compiler optimizations, new language features, backend improvements
- Important project conventions: Kotlin for compiler implementation, modular architecture, IR-based compilation
- Architecture decisions: separation of frontend/parser, IR intermediate representation, multiple backends

### Gradle Commands
- use the system installed gradle command instead of the gradle wrapper.
- `gradle build` - Full build of the compiler including running the full test suite
- `gradle clean` - Clean build artifacts
- `gradle compileKotlin` - Compile only the Kotlin source code
- `gradle installdist` - Create the compiler JARs and executable file
- `gradle installshadowdist` - Create the single "fat" compiler JAR and executable file

### Development Shortcuts
- `gradle test --tests "*TestName*"` - Run specific test classes
- `gradle build --refresh-dependencies` - Refresh dependencies during development

### Prog8 Compiler Usage
- the prog8c compiler executable can be found in the compiler/build/install/prog8c/bin folder (this is already added to the shell's path)
- the `-emu` switch can be used to directly execute the resulting program in an emulator after successful compilation.
- `prog8c -target cx16 input.p8` - Compile a Prog8 source file "input.p8" for the CommanderX16 target
- `prog8c -target cx16 -check input.p8` - Quickly check a Prog8 source file "input.p8" for compiler errors, no output binary is produced
- `prog8c -target cx16 -emu  input.p8` - Compile and execute a prog8 file in the CommanderX16 emulator
- `prog8c -target c64 -emu input.p8` - Compile and execute a prog8 file in the Commodore-64 emulator
- `prog8c -target virtual input.p8` - Compile a prog8 file for the IR/Virtual machine target
- `prog8c -target virtual -emu input.p8` - Compile and directly execute a prog8 file in the Virtual Machine
- `prog8c -vm input.p8ir` - Execute an existing prog8 program, compiled in IR form, in the Virtual Machine
- `x16emu -scale 2 -prg input.prg` - Just load an existing compiled program in the CommanderX16 emulator
- `x64sc input.prg` - run an existing compiled program in the Commodore-64 emulator (Note: You can safely ignore errors and warnings from this emulator)

### Compilation Output Files
- `*.prg` - The final compiled program file for the target system (e.g., Commander X16)
- `*.asm` - Generated assembly code from the Prog8 source
- `*.list` - Generated full assembly listing file from the Prog8 source
- `*.p8ir` - Intermediate representation file, can be executed in the Virtual Machine
- `*.vice-mon-list` - Vice emulator monitor list file for debugging

## Working Notes
[Any ongoing notes about the project that would be helpful for the assistant to remember]

## TODO Items
The file docs/source/todo.rst contains a comprehensive list of things that still have to be fixed, implemented, or optimized.
