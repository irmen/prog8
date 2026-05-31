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
- **Type Checking Tests**: Add test cases that explicitly verify:
    - Implicit assignment/comparison of incompatible types (expect failure).
    - Explicit cast (Enum ↔ Int) (expect success).
    - Explicit cast between different Enum types (expect failure).
    - Compare two different enum types (expect failure).
    - Use an enum member without proper type qualification.
- **AST Printing Verification**: Use `-printast1` and `-printast2` to verify that strongly typed enum nodes are correctly displayed in the AST output.
- **Runtime Verification**: Ensure that the code generation produces correct code for enums (e.g., correct values, proper size) using the `virtual` target and `vmtrace`.
