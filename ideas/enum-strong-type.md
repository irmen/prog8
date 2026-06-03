# Requirements

### Overview & Goals
The goal is to transition Prog8 enums from being merely syntactic sugar for integer constants into a first-class, strongly typed data type in the Prog8 language. This will improve type safety by preventing erroneous assignments or comparisons between different enums or between enums and raw integers.

### Scope
#### In Scope
- Definition of a new `EnumSubType` within the compiler's type system to extend existing `DataType`s.
- Modifications to `SymbolTable` and `TypeChecker` to enforce enum type strictness.
- Updates to AST processing phases (`SimplifiedAstMaker`) to preserve enum declarations instead of lowering them to constants.
- Code generation adjustments to handle enum variables correctly.
- Verification through new unit and integration tests.

#### Out of Scope
- Architectural changes to non-enum types.
- Backend-specific optimizations for enums (unless directly required for correctness).

# Technical Design

### Current Implementation
Currently, `Enumeration` nodes in the `compilerAst` are lowered into standard constant variable declarations (`const`) early in the `processAst()` phase. This is confirmed by `SimplifiedAstMaker.kt` which expects enums to have been removed.

### Key Decisions
1. **Represent Enum as a `DataType` with `EnumSubType`**: We will implement a new `EnumSubType` class (implementing `ISubType`) in `codeCore`. This will be used by `DataType` to uniquely identify strongly typed enums.
2. **Preserve `Enumeration` AST nodes in CompilerAST**: The `SimplifiedAstMaker` will be updated to treat `Enumeration` as a type definition during the initial compiler phases.
3. **Reduction in `SimplifiedAstMaker`**: The `SimplifiedAstMaker` will lower `EnumType` to its underlying `Int` type constants when generating the `SimpleAST`. This keeps the SimpleAST, IR, and downstream code generation simple, as they only need to deal with integers.
4. **Strict Type Checking & Casting**: The `TypeChecker` will be updated to disallow *implicit* conversions for enums. *Explicit* casting between an Enum and its underlying integer type will be allowed. Casting *between* different Enum types will be explicitly prohibited.
5. **SimplifiedAstMaker Erasure & Folding**: The `SimplifiedAstMaker` will be updated to perform the enum erasure (lowering `EnumType` to its underlying `Int` type constants) *and* the associated constant folding for enum-typed expressions that can be reduced to integers at that point. This approach centralizes the erasure logic and keeps the `ConstantFoldingOptimizer` focused on general-purpose expression simplifications.
6. **AST Printer Updates**: `AstToSourceTextConverter` (`printAst1`) will be updated to recognize and correctly output the new strongly typed enum node structure. `AstPrinter` (`printAst2`) does not need changes as it operates on the erased `SimpleAST`.

### Proposed Changes
- **`codeCore`**: Add `BaseDataType.ENUM` and introduce `EnumSubType` class implementing `ISubType` to extend `DataType`.
- **`compilerAst` / `compiler`**: Modify `SimplifiedAstMaker` and `AstPreprocessor` to stop lowering `Enumeration` to constants. Update `AstToSourceTextConverter` to support printing the typed enum nodes.
- **`simpleAst` / `SymbolTable`**: Update the `SymbolTable` and `TypeChecker` to register, validate enum types, and handle explicit casting rules (disallow implicit enum conversion, disallow cross-enum conversion).
- **Code Generators**: No changes needed. The enum type is erased to its underlying integer representation during the `SimplifiedAstMaker` phase, and the code generators only deal with plain integer values.

### Risk & Mitigations
- **Existing Code Compatibility**: Existing programs relying on implicit enum-to-integer conversion will break. This is intentional to improve type safety.
    - *Mitigation*: Provide clear compiler error messages when implicit conversion fails, and document the new explicit casting requirement.

# Testing

### Validation Approach
Primary testing uses the **virtual target** and prog8c's IR debugging features. This allows fast iteration without needing 6502 emulators.

**Debugging workflow for each change:**
1. **Syntax check**: `prog8c -check input.p8` -- quick semantic validation
2. **AST inspection**: `prog8c -target virtual -printast1 input.p8` -- inspect compiler AST (verify enum nodes are preserved)
3. **Simple AST inspection**: `prog8c -target virtual -printast2 input.p8` -- inspect Simple AST (verify enum erasure to integers)
4. **IR inspection**: `prog8c -target virtual -out /tmp/out input.p8` then examine the generated `.p8ir` file
5. **IR comparison**: `prog8c -target virtual -compareir baseline.p8ir new.p8ir` -- compare IR instruction changes when optimizing
6. **VM execution**: `prog8c -target virtual -emu input.p8` -- run and see stdout output
7. **VM trace**: `prog8c -vm input.p8ir -vmtrace` -- step through IR instructions with location tracking
8. **Disable optimizations**: `prog8c -target virtual -noopt -emu input.p8` -- isolate optimizer issues
9. **Full regression**: `gradle build --console=plain` -- run all unit tests

**Important**: Always test with `-noopt` first to isolate optimizer issues from code generation bugs. Use `-compareir` to see what instructions change when optimizations are applied.

- **Type Checking Tests**: Add test cases that explicitly verify:
    - Implicit assignment/comparison of incompatible types (expect failure).
    - Explicit cast (Enum ↔ Int) (expect success).
    - Explicit cast between different Enum types (expect failure).
    - Compare two different enum types (expect failure).
    - Use an enum member without proper type qualification.
- **AST Printing Verification**: Use `-printast1` and `-printast2` to verify that strongly typed enum nodes are correctly displayed in the AST output.
- **Runtime Verification**: Ensure that the code generation produces correct code for enums (e.g., correct values, proper size) using the `virtual` target and `vmtrace`.

Final verification against the existing test suite via `gradle build --console=plain`.

### Delivery Plan

### Stage 1: Add Enum Type Support and Erasure Logic
Enum types are introduced and correctly preserved in the compiler AST.

- Implement `EnumSubType` class in `codeCore` and add `BaseDataType.ENUM`.
- Update `CompilerAST` and `SimplifiedAstMaker` to preserve and later erase enum declarations.

### Stage 2: Implement Strict Type Checking and Casting
Strong typing for enums is enforced by the compiler.

- Update `TypeChecker` and `SymbolTable` to enforce strict type checking for enums.
- Implement explicit casting rules for enums and disallow implicit/cross-enum conversions.

### Stage 3: Verification and Testing
Enum type implementation is verified with unit and integration tests.

- Update AST printing for enums.
- Add unit tests for type safety, casting, and AST representation verification.
