
# Requirements

### Overview & Goals
Extend the Prog8 `Inliner` to support subroutines with parameters for multi-value returns and expression context calls. Additionally, enable the `inline` keyword for regular subroutines, allowing developers to explicitly suggest inlining for performance-critical code. Currently, the inliner is limited to parameterless subroutines in expression context, and the `inline` keyword is forcefully disabled for regular subroutines with a misleading warning.

### Scope
#### In Scope
- Parameterized subroutines in multi-value return assignments (e.g., `a, b = foo(x, y)`).
- Parameterized subroutines in expression context (e.g., `a = foo(x) + 1`).
- Substitution of parameter references with actual argument values (literals or simple identifiers).
- Expansion of supported return expressions to include simple binary/prefix/typecast operations.
- Enabling `inline` keyword support for regular subroutines by removing the restriction in `AstChecker`.
- Ensuring `Inliner` properly processes manually marked `inline` subroutines (including scoping analysis).

#### Out of Scope
- Support for complex arguments with side effects (calls like `foo(bar())` will still be excluded from inlining).
- Multi-statement subroutines in expression context.

### User Stories
- As a Prog8 developer, I want to use `inline` subroutines with parameters and return values so that I can write clean, modular code without the performance penalty of subroutine calls.
- As a Prog8 developer, I want the `inline` keyword to be respected for regular subroutines, so I can explicitly optimize specific parts of my code.
- As a compiler, I want to automatically inline simple parameterized subroutines to reduce execution time and code size.


# Technical Design

### Current Implementation
The `Inliner` uses a two-phase approach:
1. `DetermineInlineSubs` identifies simple subroutines and marks them as `inline`.
2. The `Inliner` itself visits call sites and replaces calls to `inline` subroutines with their bodies.

Currently, `AstChecker` disables `inline` for regular subroutines and emits a warning stating it has no effect. However, the `Inliner` already performs automatic inlining for simple regular subroutines, making this warning misleading. Furthermore, the `Inliner` explicitly rejects parameterized subroutines in expression context.

### Key Decisions
- **Parameter Substitution**: Use a specialized `AstWalker` (`ParameterSubstitutor`) to replace parameter references in the inlined body with copies of the call arguments.
- **Simple Arguments Only**: Maintain the restriction that arguments must be `NumericLiteral` or `IdentifierReference` to safely allow duplication without side-effect issues.
- **Scoping**: Ensure `makeFullyScoped` is called on all inlineable subroutines (including those manually marked `inline`) to prevent name collisions after inlining.
- **Respect `inline` Keyword**: Stop unsetting the `inline` flag in `AstChecker` for regular subroutines. This allows the user to suggest inlining even for cases that might not be picked up by the automatic inliner's heuristic (though they must still meet basic inlineability criteria).

### Proposed Changes
#### `Inliner.kt`
- **`ParameterSubstitutor`**: A new private class to perform the replacement of parameters with arguments.
- **`substitute(node, paramMap)`**: A helper function to apply substitution to a copied node.
- **`after(Assignment)`**: Implement zipping of `sub.parameters` and `fcall.args` to perform substitution in all return values of a multi-assignment.
- **`before(FunctionCallExpression)`**: Implement substitution for single-value returns.
- **`possiblyInlineFunctionBody`**: Use substitution for void calls, fixing a bug where parameters used in the body were incorrectly handled.
- **`DetermineInlineSubs`**:
    - Expand `isBodyInlineable` to allow `BinaryExpression`, `PrefixExpression`, and `TypecastExpression`.
    - Modify the visit condition to process all regular subroutines (not just those with `inline=false`), ensuring `makeFullyScoped` is called for manually marked `inline` subroutines.
    - If a subroutine is already `inline`, verify it meets the inlineability criteria; if not, it will naturally fail at call sites via `canInlineAtCallSite`.

#### `AstChecker.kt`
- Remove the block that disables `inline` for regular subroutines and emits the "no effect" warning.

### Data Models / Contracts
- `paramMap`: `Map<VarDecl, Expression>` where `VarDecl` is the parameter declaration and `Expression` is the argument passed at the call site.


# Testing

### Validation Approach
Verification will be performed using the automated test suite in `TestOptimization.kt`.

### Key Scenarios
- **Multi-return with parameters**: `a, b = foo(1, 2)` where `foo(x, y)` returns `x+1, y+1`.
- **Expression context with parameters**: `a = foo(10) + gv`.
- **Void call with used parameters**: `foo(10)` where `foo(x)` calls `txt.print_ub(x)`.
- **Nested inlining**: `a = foo(bar(10))` where both are inlineable.
- **Manual `inline` keyword**: Verify that `inline sub foo() { return 1 }` is inlined and no longer emits a "no effect" warning.

### Test Changes
- Enable `xtest("inline call with one return value and one parameter")`.
- Enable `xtest("inline call with two return values and two parameters")`.
- Add new tests for complex return expressions and void calls with parameters.


# Delivery Steps

###   Step 1: Relax inlining restrictions
Relax inlining restrictions for regular subroutines.
- Modify `AstChecker.kt` to remove the block that forcefully disables the `inline` flag on regular subroutines and emits a "no effect" warning.
- Update `canInlineAtCallSiteWithReason` in `Inliner.kt` to allow expression context calls (including multi-value returns) for subroutines with parameters, provided all arguments are simple (literals or identifiers).
- Update `DetermineInlineSubs` in `Inliner.kt` to ensure it processes all regular subroutines for scoping (calls `isBodyInlineable` even if already marked `inline`).

###   Step 2: Implement parameter substitution logic
Implement parameter substitution logic.
- Add a private `ParameterSubstitutor` class in `Inliner.kt` that inherits from `AstWalker` and replaces `IdentifierReference` nodes that target subroutine parameters with copies of the corresponding argument expressions.
- Expand `isBodyInlineable(Return)` in `DetermineInlineSubs` to allow `BinaryExpression`, `PrefixExpression`, and `TypecastExpression` as return values if their operands are also inlineable. This enables inlining of subroutines like `return x + 1`.

###   Step 3: Complete inlining implementation
Complete inlining implementation for all call contexts.
- Update `Inliner.after(Assignment)` to handle multi-value returns from parameterized subroutines by zipping parameters with arguments and applying `ParameterSubstitutor` to each return value.
- Update `Inliner.before(FunctionCallExpression)` to handle single-value returns from parameterized subroutines using `ParameterSubstitutor`.
- Update `possiblyInlineFunctionBody` to handle void calls with parameters by applying `ParameterSubstitutor` to the inlined body statement. This fix ensures that parameters used in the body are correctly replaced by argument values.

###   Step 4: Verification and Testing
Verify the changes with tests.
- Enable the previously disabled `xtest` cases in `TestOptimization.kt` that were blocked by missing parameter substitution.
- Add new test cases to `TestOptimization.kt` covering void calls with used parameters, complex return expressions involving parameters, and the `inline` keyword on regular subroutines.
- Run the full compiler test suite to ensure no regressions in other optimization passes or code generation.
