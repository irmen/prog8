# Linked List Iteration Plan

## Goal

Add general linked-list iteration to Prog8 without hardcoding Amiga Exec conventions.

Supported forms:

```prog8
for node in start via .next { }                 ; until 0 by default
for node in start via .next until sentinel { }  ; explicit terminator
for node in list { }                            ; header shorthand, forward
```

All forms desugar to ordinary `while` loops in `CodeDesugarer`, so no backend changes are needed.

> **Implementation status:** the forms above and conventional-name header shorthand
> are implemented. Struct attributes (`@head`, `@succ`, etc.) described later in this
> document are not yet implemented.

## Syntax

### Grammar

File: `parser/src/main/antlr/Prog8ANTLR.g4`

Current rule:

```antlr
forloop : 'for' datatype? scoped_identifier 'in' expression (STEP expression)? EOL? (statement | statement_block) ;
```

Proposed rule:

```antlr
forloop : 'for' datatype? scoped_identifier 'in' iterable=expression
          (VIA '.' viaField=identifier)?
          (UNTIL untilExpr=expression)?
          (STEP stepExpr=expression)?
          EOL? (statement | statement_block) ;
```

Add named lexer tokens for `VIA` and `UNTIL` rather than using anonymous parser
literals, because the visitor needs stable token accessors.
The link field must use the existing `identifier` rule; there is no generic
`IDENTIFIER` token in this grammar.

For example, add the tokens alongside the existing keyword tokens:

```antlr
VIA: 'via';
UNTIL: 'until';
```

Use the existing zero expression (`0`) as the default and explicit null
sentinel. Do not add a first-class `null` literal for this feature.

Notes:

- `via` and `until` are only meaningful for linked-list iteration.
- `step` remains valid only for ordinary array/string/range loops.
- The parser should accept the combined forms; semantic validation rejects invalid combinations.

### Parser visitor

File: `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt`

Method: `visitForloop(ctx: ForloopContext)`

Current implementation:

```kotlin
val loopvar = ctx.scoped_identifier().accept(this) as IdentifierReference
val iterable = ctx.expression(0).accept(this) as Expression
val scope = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
val loopVarType = dataTypeFor(ctx.datatype())
val step = if(ctx.STEP()!=null && ctx.expression().size > 1) ctx.expression(1).accept(this) as Expression else null
return ForLoop(loopvar, iterable, scope, loopVarType, step, ctx.toPosition())
```

Proposed implementation shape:

```kotlin
val loopvar = ctx.scoped_identifier().accept(this) as IdentifierReference
val iterable = ctx.expression(0).accept(this) as Expression
val scope = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
val loopVarType = dataTypeFor(ctx.datatype())

val viaField = ctx.viaField()?.text         // only the identifier under VIA
val untilExpr = ctx.untilExpression()?.accept(this) as Expression?
val step = ctx.stepExpression()?.accept(this) as Expression?

return ForLoop(
    loopVar = loopvar,
    iterable = iterable,
    body = scope,
    loopVarType = loopVarType,
    step = step,
    position = ctx.toPosition(),
    linkedVia = viaField,
    linkedUntil = untilExpr
)
```

The grammar should expose labeled contexts such as `untilExpression=expression`
and `stepExpression=expression`, and the visitor must use those labels. Do not
infer clause meaning from `ctx.expression().size`: for example, `via .next
step -1` must remain an invalid linked-loop combination, not reinterpret `-1`
as the sentinel.

## AST Changes

File: `compilerAst/src/prog8/ast/statements/AstStatements.kt`

Extend `ForLoop`:

```kotlin
class ForLoop(var loopVar: IdentifierReference,
              var iterable: Expression,
              var body: AnonymousScope,
              var loopVarType: DataType?,
              var step: Expression?,
              override val position: Position,
              var linkedVia: String? = null,
              var linkedUntil: Expression? = null,
              var linkedReverse: Boolean = false,
              ) : Statement()
```

Keep `position` before the new defaulted parameters so existing positional
construction remains source-compatible, and prefer named arguments for new
construction sites.

Update:

- `linkParents()` must link `linkedUntil`.
- `replaceChildNode()` must handle `linkedUntil`.
- `toString()` should include the new fields for debugging.
- `referencesIdentifier()` must include `linkedUntil`.
- `IAstVisitor.visit(ForLoop)` and `AstWalker.visit(ForLoop)` must visit
  `linkedUntil`, otherwise identifier replacement, constant folding, and type
  checking will skip the sentinel expression.

Example:

```kotlin
override fun linkParents(parent: Node) {
    this.parent = parent
    loopVar.linkParents(this)
    iterable.linkParents(this)
    step?.linkParents(this)
    linkedUntil?.linkParents(this)
    body.linkParents(this)
}
```

```kotlin
override fun replaceChildNode(node: Node, replacement: Node) {
    when {
        node === loopVar -> loopVar = replacement as IdentifierReference
        node === iterable -> iterable = replacement as Expression
        node === step -> step = replacement as Expression
        node === linkedUntil -> linkedUntil = replacement as Expression
        node === body -> body = replacement as AnonymousScope
        else -> throw FatalAstException("invalid replace in $this at ${node.position}: $node -> $replacement")
    }
    replacement.parent = this
}
```

## Type Checking

File: `compiler/src/prog8/compiler/astprocessing/AstChecker.kt`

Method: `visit(forLoop: ForLoop)`

Add an early branch before the normal iterable checks:

```kotlin
if (forLoop.linkedVia != null || forLoop.linkedReverse || isLinkedHeaderShorthand(forLoop)) {
    checkLinkedForLoop(forLoop)
    super.visit(forLoop)
    return
}
```

Implement `checkLinkedForLoop(forLoop)`:

1. `loopVar.targetVarDecl()` must exist and have a pointer base type.
2. `iterable.inferType(program)` must be a pointer to the same node type, or a list header type with resolvable metadata.
3. `linkedVia` field must exist on the node type and be a pointer to the same node type.
4. `linkedUntil`, if present, must be comparable to the node pointer type.
5. If `linkedReverse` is true, a backward link must be available.
6. Reject `step` when linked-list options are present.

For header shorthand (`for node in list` with no `via`), require the iterable
type to have resolvable list metadata. `isLinkedHeaderShorthand` must only
return true for a pointer/list-header type with linked-list metadata; ordinary
array, string, range, and other existing `for` loops must continue through the
normal checks. If the expression is intended as a header but metadata cannot
be resolved, report:

```text
linked-list iteration requires 'via .field', or a list type with @head/@succ/@tail metadata
```

## Desugaring

File: `compiler/src/prog8/compiler/astprocessing/CodeDesugarer.kt`

Method: `after(forLoop: ForLoop, parent: Node)`

Add a new branch before the existing array/string reverse handling:

```kotlin
if (forLoop.linkedVia != null || forLoop.linkedReverse || isLinkedHeaderShorthand(forLoop)) {
    return desugarLinkedForLoop(forLoop, parent)
}
```

### Explicit form

Input:

```prog8
for node in start via .next until sentinel { body }
```

Output AST equivalent to:

```prog8
node = start
while node != sentinel {
    body
    node = node.next
}
```

Implementation steps:

1. Resolve `startExpr = forLoop.iterable`.
2. Resolve `linkField = forLoop.linkedVia ?: resolvedDefaultLink`.
3. Resolve `sentinelExpr = forLoop.linkedUntil ?: NumericLiteral.optimalInteger(0, pos)`.
4. Create assignment `node = startExpr`.
5. Create condition `node != sentinelExpr`.
6. Create body:
   - original `forLoop.body`
   - assignment `node = node.linkField`
7. Replace `ForLoop` with a `VarDecl`? No. The loop variable must already be declared or declared by the for loop. For a typed for loop, create the variable declaration first if needed, matching existing for-loop behavior. For an untyped for loop, reuse the existing variable.
8. Return a list of `AstInsertBefore`, `AstReplaceNode`, or `AstInsertAfter` modifications, depending on how other desugarers in this file operate.

Update `ImplicitForIteratorDecls` as part of this work. It currently only
creates implicit loop variables for recognized iterable types, so pointer
starts and list headers would otherwise reach desugaring without a declaration.
The linked-loop path must infer the node pointer type, validate an existing
declaration or insert a scoped declaration for an untyped iterator, and keep
symbol-table/parent links valid before constructing assignments.

The implementation should mirror `desugarReverseArrayStringForLoop`, which already builds synthetic `VarDecl`, `Assignment`, `WhileLoop`, and `AnonymousScope` nodes.

### Header shorthand

For `for node in list`, resolve metadata first:

```kotlin
data class LinkedMetadata(
    val start: Expression,
    val link: String,
    val sentinel: Expression
)
```

Resolution order:

1. Struct attributes:
   - `@head`, `@succ`/`@next`, `@pred`/`@prev`, `@tail`
2. Conventional field names:
   - head: `head`, `first`
   - succ: `succ`, `next`
   - pred: `pred`, `prev`, `prevNode`
   - tail: `tail`, `last`
3. If the iterable is already a node pointer, use it directly and default to `next`/`succ` with null terminator.

If resolution fails, leave the node unchanged and let `AstChecker` report the error.

### Attribute support

> **Not implemented yet.** The current implementation uses only conventional field
> names; attributes are future work.

Attributes can be added to struct fields using the existing attribute mechanism. If Prog8 does not already support field attributes, add a minimal `@head`, `@succ`, `@pred`, and `@tail` marker that is stored on `VarDecl` or struct field metadata.

Files likely involved:

- `compilerAst` struct field representation
- parser grammar for struct fields
- `AstChecker` for attribute validation

If attributes are too large for the first implementation, ship conventional names first and add attributes later.

## Simplified AST

No changes should be needed in `simpleAst` if desugaring happens in `CodeDesugarer`, because the resulting `WhileLoop` and `Assignment` nodes are already supported by `SimplifiedAstMaker`.

## Backend Impact

No backend changes:

- `codeGenCpu6502`
- `codeGenIntermediate`
- `codeGenVirtual`
- `codeGenM68k`

All traversal logic is removed during desugaring.

## Tests

Files to update or add:

- `compiler/test/ast/TestProg8Parser.kt`
  - parse `via` and `until`
- `compiler/test/ast/TestVariousCompilerAst.kt`
  - verify AST fields
- `compiler/test/TestCompilerOnRanges.kt` or a new `TestLinkedForLoops.kt`
  - desugaring output
- `compiler/test/codegeneration/TestConstStepForLoops6502.kt` or a new linked-list codegen test
  - generated traversal
- `compiler/test/vm/TestVariableStepForLoops.kt` or a new VM test
  - execution behavior

Test cases:

1. Singly linked list, default null terminator.
2. Explicit `until 0`.
3. Custom sentinel.
4. Exec-style forward traversal with `&list.tail`.
5. Backward traversal with `pred`.
6. Custom field names using `via`.
7. Header shorthand with conventional names.
8. Header shorthand with attributes.
9. Invalid `step` combination.
10. Missing link field.
11. Wrong link field type.
12. Non-pointer loop variable.
13. `-noopt` and optimized builds match.

## Implementation Order

1. Add grammar and parser visitor.
2. Add AST fields.
3. Add type checking.
4. Add explicit-form desugaring.
5. Add custom sentinel support.
7. Add conventional-name header shorthand.
8. Add attributes.
9. Add tests.

## Risks

- Expression indexing in the parser visitor is fragile; capture contexts explicitly.
- Header shorthand resolution can become implicit and surprising; attributes should be preferred.
- Exec list termination uses `&list.tail`, not `list.tail`.
- List mutation during traversal is allowed but can invalidate terminators; document this.
- Conventional names may collide with unrelated fields; attributes resolve ambiguity.
