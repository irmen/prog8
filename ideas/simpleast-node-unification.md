
# Requirements

### Overview & Goals
The objective is to evaluate and implement a unification of the `simpleAst` node hierarchy by making all nodes inherit from an expression-like base. Currently, `simpleAst` distinguishes between `PtNode` (base for statements and structural elements) and `PtExpression` (base for nodes with a data type and value). 

### Evaluation: Is it worth it?
After investigating the codebase, the assessment of this change is as follows:

**Pros:**
- **Simplified AST Manipulation**: Many optimizers (in `prog8.code.optimize`) currently have to check for specific node types. Unifying them allows for more generic transformations.
- **Logical Unification**: Nodes like `if` exist in two forms (`PtIfElse` and `PtIfExpression`). Merging them reduces redundancy and makes the AST more representative of the language's semantics.
- **Enables Block Expressions**: Making `PtNodeGroup` (blocks) have a type (the type of their last expression) naturally enables block-expressions.
- **Cleaner Backends**: `AsmGen` and `IRCodeGen` can use a more uniform visitor/translation pattern.

**Cons/Risks:**
- **Substantial Churn**: This is a cross-cutting change affecting almost 100 classes in `simpleAst` and the core logic of all code generators.
- **Backend Complexity**: The 6502 backend must remain efficient. We must ensure that "expression-nodes" used in "statement-contexts" don't generate redundant `LDA`/`STA` instructions.
- **Loss of Static Typing**: Removing the `PtExpression` interface means we lose the ability to restrict some function parameters to only accept value-producing nodes at compile-time.

**Verdict**: **Yes, it is worth it**, especially as the compiler moves towards more sophisticated optimizations. The current separation is a technical debt that complicates the implementation of new language features and optimization passes.

### Scope
- **In Scope**:
    - Refactoring `PtNode` to include a `DataType` property.
    - Merging `PtExpression` into `PtNode`.
    - Updating all `PtNode` subclasses in `simpleAst` module.
    - Unifying `PtIfElse` and `PtIfExpression` into a single `PtIf` node.
    - Updating `SimplifiedAstMaker` to use the unified hierarchy.
    - Updating `AsmGen6502` and `IRCodeGen` to handle the unified nodes.
- **Out of Scope**:
    - Changes to the Prog8 source language syntax.
    - Changes to the `compilerAst` module.
    - Implementing new language features (this is a refactoring task).


# Technical Design

### Proposed Changes

#### 1. Unified `PtNode` Base Class
The current `PtNode` will be updated to include a mandatory `DataType`. For nodes that do not return a value, `DataType.UNDEFINED` will be used.

```kotlin
// simpleAst/src/prog8/code/ast/AstBase.kt
sealed class PtNode(val type: DataType, val position: Position)
```

#### 2. Merging `PtExpression`
The `PtExpression` class will be removed. All existing subclasses of `PtExpression` will now inherit directly from `PtNode` (or `PtNamedNode`).

#### 3. Node Unification
- **`PtIfElse` & `PtIfExpression`**: Merged into `PtIf`. If used as a statement, its type is `UNDEFINED`. If used as an expression, its type is the common type of both branches.
- **`PtFunctionCall`**: Already largely unified, but will now consistently be a `PtNode` with a return type (which may be `UNDEFINED` for void calls).
- **`PtNodeGroup` (Blocks)**: Will have a type corresponding to its last child (or `UNDEFINED` if empty).

#### 4. Impact on Code Generation
The code generators (`AsmGen6502` and `IRCodeGen`) currently have separate entry points for statements and expressions:
- `translate(stmt: PtNode)`
- `assignExpressionToRegister(expr: PtExpression, ...)`

With unification, these can be streamlined. However, to maintain efficiency on 6502, the generator must still know when an expression's result can be discarded to avoid redundant register loads.

### Risks & Mitigations
- **Refactoring Churn**: This is a cross-cutting change affecting almost all parts of the backends.
    - *Mitigation*: Perform a staged migration. First, update the base class and fix compilation errors by assigning `UNDEFINED` to former statement nodes. Then, incrementally unify specific node pairs.
- **Code Generation Regressions**: Discarding expression results incorrectly.
    - *Mitigation*: Ensure `AsmGen` maintains a clear path for "evaluate for side effect" vs "evaluate for value".


# Testing

### Validation Approach
Existing tests in `simpleAst` and backends must pass.

### Key Scenarios
- **Literal and Binary Expression Type Correctness**: Verify that nodes formerly inheriting from `PtExpression` correctly retain their type info after merging.
- **If-Expression Unification**: Verify that an `if` used in an expression context correctly propagates its type, and an `if` used as a statement remains `UNDEFINED`.
- **Backend Stability**: Compare IR and Assembly output before and after refactoring to ensure no functional regressions.


# Delivery Steps

###   Step 1: Refactor PtNode Base and update Subclasses
Implementation of the new unified base class.
- Modify `PtNode` in `AstBase.kt` to include `type: DataType`.
- Update all subclasses of `PtNode` to pass a `type` to the base constructor.
- Remove `PtExpression` and update its subclasses to inherit from `PtNode`.
- Fix all compilation errors in constructors and factory methods.

###   Step 2: Update AST Maker and Unify Control Flow Nodes
Simplification of the AST construction logic and unification of redundant nodes.
- Update `SimplifiedAstMaker.kt` to handle the unified hierarchy.
- Merge `PtIfElse` and `PtIfExpression` into a single `PtIf` node.
- Update `PtNodeGroup` to track the type of its last statement.

###   Step 3: Update Backends and Validate
Updating the code generators to work with the unified nodes.
- Update `AsmGen6502` and `IRCodeGen` to handle the unified `PtNode` hierarchy.
- Ensure that "evaluate for side-effect" vs "evaluate for value" logic is preserved for 6502 performance.
- Run all tests and verify output parity using `-compareir`.
