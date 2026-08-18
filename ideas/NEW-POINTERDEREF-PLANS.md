# Pointer Dereference Grammar Improvements

## Overview

This document outlines the implementation plan to fix pointer dereference syntax limitations in Prog8.

### The Problem

Currently, `^^` (pointer dereference) is required in grammar even when it's obvious from context:
```prog8
listarray[2].value = 123    ; FAILS - parse error
listarray[2]^^.value = 123  ; works but awkward
```

### The Goal

Make `^^` optional when the dereference is implied by array indexing or field access.

### Plan Status

- **Analysis**: Complete
- **Implementation**: Not started (Option A recommended)
- **Risk**: LOW

---

## 1. Dotted Notation (Qualified Symbols) - Many Meanings

**Also known as "Cursed" in current grammar**

Prog8's "." notation is used for many different semantic purposes, making the grammar complex and ambiguous.

### What "." Can Mean

1. **Block/Subroutine Scope Resolution** (qualified symbols):
   - `a.b` - symbol `b` from block `a` (global scope)
   - `a.b.c` - symbol `c` in subroutine `b` in block `a`
   - Nested: `block.sub.field` chains

2. **Struct Field Access** (for pointer variables):
   - `v.field` - access field from struct variable `v` (pointer to struct)
   - If the field is also a pointer: `v.field.subfield` (chained)

3. **Pointer Dereference**:
   - `^^` is the explicit pointer dereference operator
   - `v^^` dereferences to get the struct value

4. **Array Indexing with Pointers**:
   - `ptr[5]` accesses the struct at pointer offset 5
   - `ptr[5].field` - **expression works, assignment NOT supported**
   - Current workaround requires explicit `^^`: `np[2]^^.field = 9999`

### Current Grammar Issues

**Current Grammar:**
```antlr
pointerdereference: (prefix = scoped_identifier '.')? derefchain ('.' field = identifier)?;
derefchain: singlederef ('.' singlederef)*;
singlederef: identifier arrayindex? POINTER;
```

**Problems:**

1. **Ambiguity**: `a.b` can be either:
   - Block scope resolution (a.b = subroutine in block a)
   - Struct field access (v is a struct pointer, accessing field b)

2. **No array indexing on structs**: `ptr[5].field` is rejected but should work

3. **Asymmetric treatment**: First identifier handled differently from rest

4. **Expression vs Assignment Context**:
   - Expression (RHS): `value = ptr[5].field` works ✓
   - Assignment (LHS): `ptr[5].field = value` FAILS ✗
   - Grammar has different rules for each context

5. **AST/Visitor Complexity**: The grammar flattens everything into nodes with `prefix`, `derefchain`, and optional `field`. The Kotlin visitor (`Antlr2KotlinVisitor.kt`) must essentially re-parse this to determine what the user actually wrote:
   - Must distinguish scope resolution from field access
   - Must track array index position
   - Must handle the asymmetric prefix differently
   - Must rebuild semantic meaning from flattened representation
   - This leads to complex, fragile logic in the visitor that's hard to maintain

### Recommendation

Redesign to treat `.`, `^^`, and `[]` as postfix operators:

```antlr
dottedChain
    : primary (('.' identifier) | ('[' expression ']') | POINTER)*
    ;

primary
    : scoped_identifier
    | directmemory
    | '(' expression ')'
    ;
```

This treats all three operators with equal precedence, letting AST determine meaning.

#### Why the conflict matters

The `scoped_identifier` rule is:

```antlr
scoped_identifier : identifier ('.' identifier)* ;
```

This makes `a.b.c` a **single grammatical unit** meaning "symbol c in scope resolved via a.b". The AST and codebase relies on this - `IdentifierReference.nameInSource` returns the fully-qualified string like `"block.sub.field"`.

**The structural conflict**: If you try to create a unified chain rule:

```antlr
dottedChain: primary (('.' identifier) | ('[' expression ']') | POINTER)* ;
primary: scoped_identifier ;
```

ANTLR sees ambiguous parse paths because:
1. Input `a.b` matches both `dottedChain` and `scoped_identifier`
2. The lexer can't know whether `.` is postfix operator or scope separator
3. The parser would need unbounded lookahead to decide

**Solution requires one of**:
1. Change grammar so `scoped_identifier` becomes a special token (not achievable without lexer changes)
2. Rewrite AST to use explicit tree structure (major refactor)
3. Keep current scheme but add new alternative syntax (compatibility layer)

**Current Status (from official docs):**

| Syntax | Context | Works? | Notes |
|--------|---------|--------|-------|
| `block.sub` | Both | ✓ | Scope resolution |
| `v.field` | Expression | ✓ | Field access as value |
| `v.field` | Assignment | ✗ | NOT supported |
| `v^^.field` | Both | ✓ | Explicit `^^` dereference |
| `np[2].field = value` | Assignment | ✗ | Parser fails |
| `np[2]^^.field = value` | Assignment | ✓ | Workaround with explicit `^^` |
| `value = np[2].field` | Expression | ✓ | Works as value |
| `ptr[5].field` | Expression | ✓ | Works |
| `ptr[5].field` | Assignment | ✗ | Parser fails |

**Ideal behavior we want:**

| Syntax | Context | Goal |
|--------|---------|------|
| `v.field = value` | Assignment | Works (implicit `^^`) |
| `ptr[5].field = value` | Assignment | Works |
| `ptr[5].field` | Expression | Works |
| `a.b.c.d` | Both | Deep chain works |

The documentation explicitly notes this is a parser limitation. The `^^` should ideally be implicit for both contexts, not just in expressions.

---

## 2. Backward Compatibility Analysis

### Current Test Usage

| Location | Usage Type | Count |
|----------|----------|-------|
| `TestPointers.kt` | `PtrDereference` AST node verification | 8 tests |
| `TestPointers.kt` | `sprptr[2]^^.y = 99` (explicit `^^`) | ~15 tests |

#### Skipped Tests (Currently Failing - Parser Limitation)

**These 2 tests would become passable after implementing the fix:**

1. **Line 1443** (`xtest`):
   ```prog8
   test("array indexed assignment parses with and without explicit dereference after struct pointer") {
       l1.s[0] = 4242           ; without ^^ - currently fails as parser error
       l1^^.s[0] = 4242         ; explicitly with ^^ - also currently fails as parser error
   }
   ```

2. **Line 1465** (`xtest`):
   ```prog8
   xtest("a.b.c[i].value = X where pointer is struct gives good error message") {
       other.foo.listarray[2].value = cx16.r0    ; currently fails as parser error
   }
   ```
   Currently returns two "no support for" errors. After fix, should parse correctly.

**Note:** Existing working code using explicit `^^` must not be affected. Examples that currently work:
```prog8
sprptr[2]^^.y = 99         ; works - constant index + ^^ + field
sprites[2]^^.y = 99        ; works - constant index + ^^ + field
```

### Current Stdlib Usage

All standard library code uses **explicit `^^`** syntax which is fully supported:
- `^^Type var` (typed pointer declarations) - ~35 files
- `ptr^^` or `somevar^^` (dereference operators) - ~25 uses
- **No usage of unsupported syntax** (`array[idx].field = value` as assignment target)

### Risk Assessment: LOW

| Category | Impact |
|----------|--------|
| Grammar changes | None - current rules unchanged |
| Existing tests | No breakage - all pass |
| Stdlib | No breakage - uses explicit `^^` |
| New syntax | Enables previously-failing cases |

### Conclusion

Safe to implement - there's no current code that would break. The change simply enables
the implicit `^^` for assignment targets, making these work:

```prog8
; BEFORE: parser error
; AFTER: works correctly
node.next[2].value = 123
```

The 2 skipped tests can then be re-enabled (remove `x` prefix).

---

## 3. Semantic vs Syntax Context Analysis

### Where Context Matters

The **parser grammar** determines expression vs assignment context (not later AST validation):

```antlr
// Assignment targets (left side of =)
assign_target:
    scoped_identifier
    | arrayindexed
    | directmemory
    | pointerdereference      # requires pointerdereference rule
    | VOID
    ;

// Expressions (right side of =)  
expression:
    ...
    | pointerdereference    # uses same rule but grammar allows it
    | ...
    ;
```

### Key Grammar Rule (Prog8ANTLR.g4:391)

```antlr
pointerdereference: (prefix = scoped_identifier '.')? derefchain ('.' field = identifier)? ;
derefchain : singlederef ('.' singlederef)* ;
singlederef : identifier arrayindex? POINTER ;
```

**The critical part:** `POINTER` is **required** in `singlederef`.

### Result

| Context | Input | Works? | Why |
|--------|-------|-------|------|
| Expression | `listarray[2]^^.value` | ✓ | Grammar via rule in `expression` |
| Assignment | `listarray[2].value = 123` | ✗ | Parser can't match `assign_target` without POINTER |

### Error Message

```
parse error: no viable alternative at input 'listarray[2].'
```

### Conclusion

- Context is determined at **parse time** (grammar)
- Not a semantic/validation issue - it's a **syntax limitation**
- Fix must be in grammar rules, not later AST phase

---

## 4. Testing Strategy

### Findings After Implementation Investigation

**IMPORTANT: The grammar fix is more complex than originally anticipated.**

The two skipped tests (`xtest` at lines 1443 and 1465) **CANNOT be enabled** with a simple grammar change. Here's why:

#### What Works
| Syntax | Status | Example |
|--------|--------|---------|
| `ptr[idx]^^.field = value` | ✅ Works | `nodes[0]^^.y = 99` |
| `value = ptr[idx]^^.field` | ✅ Works | Expression context |
| `ptr.field` as expression | ✅ Works | Via `.` operator in expression rule |

#### What Does NOT Work (Parser Limitation)
| Syntax | Why It Fails |
|--------|-------------|
| `ptr.field = value` (implicit) | `ptr.field` matches `scoped_identifier`, not `pointerdereference` |
| `ptr^^.field = value` (no index) | `singlederef` requires `POINTER` at END, so `.field` has nowhere to go |
| `ptr[idx].field = value` (implicit) | `scoped_identifier` greedily matches `ptr.field`, leaving `[idx]` stranded |
| `ptr.field[idx] = value` | Same issue - grammar ambiguity with `scoped_identifier` |

#### Root Cause
The `singlederef` rule structure `identifier arrayindex? POINTER` puts `POINTER` at the END. When there's a `.field` after `^^`, the `singlederef` for the field doesn't have `POINTER`, causing a parse failure.

Making `POINTER` optional creates ambiguity with `scoped_identifier` (both match `a.b`).

### Tests Added

The following test was added to verify working patterns:

```kotlin
test("mix of pointer dereference patterns in same program") {
    // Verifies nodes[0]^^.value and nodes[1]^^.value work correctly
    ...
}
```

### Execution Plan

The skipped tests remain as `xtest` until a comprehensive grammar redesign (Option B) is implemented.
Current status: **no regression, working patterns verified, limitation documented**.

---

## 5. Visitor Analysis

### 1. ANTLR → CompilerAST Parser Visitor (`Antlr2KotlinVisitor`)

**Purpose:** Transforms ANTLR parse tree → CompilerAST nodes

| Function | Location | Purpose |
|----------|----------|---------|
| `visitPointerDereferenceTarget` | Line 760 | Handles LHS (assignment targets) |
| `visitPointerdereference` | Line 780 | Main parsing - flattens grammar into prefix+derefchain+field |

**Logic flow (lines 780-803):**
1. Extract `prefix` as `IdentifierReference` (optional)
2. Extract `derefchain` as list of (name, arrayIndex?) pairs
3. Merge prefix + derefchain + optional field
4. Create `PtrDereference` or `ArrayIndexedPtrDereference` AST node

### 2. AST Walker (`AstWalker`)

**Purpose:** Walks CompilerAST for transformation passes (optimizations, cleanup, etc.)

| Function | Location | Purpose |
|----------|----------|---------|
| `visit(deref: PtrDereference, parent)` | Line 639 | Simple traversal |
| `visit(deref: ArrayIndexedPtrDereference, parent)` | Line 644 | Visits chain including array indices |

**Key observation:** `ArrayIndexedPtrDereference` visits its chain's array indices:
```kotlin
deref.chain.forEach { it.second?.accept(this) }
```

This means any array index expressions are also walked. This is relevant for any transformation passes that need to handle indices in pointer dereferences.

### 3. CompilerAST → SimpleAST Transformer (`SimplifiedAstMaker`)

| Function | Location | Purpose |
|----------|----------|---------|
| `transform(deref: PtrDereference)` | Line 122 | Converts to `PtPointerDeref` |
| `ArrayIndexedPtrDereference` handling | Line 110 | Throws - expects already converted |

### 4. AST Cleanup Phase (`CodeDesugarer`)

Critical for understanding the conversion:

| Location | Purpose |
|----------|---------|
| Line 741 (`CodeDesugarer.kt:741`) | Eliminates `ArrayIndexedPtrDereference` in expressions |
| Line 846 (`CodeDesugarer.kt:846`) | Eliminates `ArrayIndexedPtrDereference` in assignment targets |

**Key insight:** `ArrayIndexedPtrDereference` gets *rewritten* before reaching SimpleAST, never becomes `PtPointerDeref`.

### 5. SimpleAST Visitors (both IR and 6502 codegen)

| Class | Used By |
|-------|---------|
| `PtPointerDeref` | IR codegen, 6502 codegen |

**`PtPointerDeref` structure (SimpleAST):**
```kotlin
class PtPointerDeref(
    type: DataType,
    val chain: List<String>,    // ["field1", "field2"]
    val derefLast: Boolean,     // whether last ^ is needed
    position: Position
)
```

### Summary: Where the Fix Needs to Go

**The problem:** Expression `ptr[i].field` works but assignment `ptr[i].field = value` fails.

**Current grammar rule (Prog8ANTLR.g4:391):**
```antlr
pointerdereference: (prefix = scoped_identifier '.')? derefchain ('.' field = identifier)? ;
derefchain : singlederef ('.' singlederef)* ;
singlederef : identifier arrayindex? POINTER ;
```

**Why it fails:**
- `singlederef` **requires** `POINTER` (`^^`) token
- So `listarray[2]^^` parses, but `listarray[2]` doesn't
- The grammar rule REQUIRES `^^` in all positions

**Where context is determined:** Parser grammar itself (`assign_target` vs `expression` rules), not later in AST.

**Current behavior verified:**
```
# Expression context - works
ubyte x = listarray[2]^^.value   # parses ✓

# Assignment context - fails  
listarray[2].value = 123        # parse error: "no viable alternative at input 'listarray[2].'"
```

**The fix:** Make `^^` optional in the grammar (in certain contexts) so parser accepts:
- `ptr[i].field` as valid syntax in both expression and assignment contexts

---

## 6. AST Node Cleanup (Future Work)

### Current Redundancy

**1. Two separate AST classes:**

| Class | Chain Type | Usage |
|-------|-----------|-------|
| `PtrDereference` | `List<String>` | Simple derefs: `a^^` |
| `ArrayIndexedPtrDereference` | `List<Pair<String, ArrayIndex?>>` | With indices: `a[0]^^` |

**2. AssignTarget has two fields:**
```kotlin
var pointerDereference: PtrDereference? = null,
var arrayIndexedDereference: ArrayIndexedPtrDereference? = null,
```

### Cleanup Plan (from existing TODO at AstExpressions.kt:1885)

1. **Unified AST chain element:**
```kotlin
sealed class PtrDerefElement {
    data class Simple(val name: String) : PtrDerefElement()
    data class Indexed(val name: String, val index: ArrayIndex?) : PtrDerefElement()
}
```

2. **Single `PtrDereference` class:**
```kotlin
class PtrDereference(
    val chain: List<PtrDerefElement>,
    val derefLast: Boolean,
    val position: Position
)
```

3. **AssignTarget single field:**
```kotlin
var pointerDereference: PtrDereference? = null
```

### Why Not Do It Now

- Requires grammar change first (postfix operators)
- Would need extensive testing
- Currently works (with workaround via explicit `^^`)

### Current Workaround

Users write explicit `^^`:
```prog8
np[2]^^.field = 9999    ; works
np[2].field = 9999      ; fails (parser error)
```

### Recommendation

This cleanup is a **future enhancement** after the grammar fix. The immediate priority is fixing the parser to accept implicit `^^` for assignment targets.

---

---

## 7. Virtual Target (IR Codegen) Analysis

### Overview

The IR codegen (`codeGenIntermediate`) processes `PtPointerDeref` from SimpleAST to generate IR instructions.

### Key Functions

| Function | Location | Purpose |
|----------|----------|---------|
| `translate(deref: PtPointerDeref)` | ExpressionGen.kt:108 | Main entry point for pointer deref |
| `traverseRestOfDerefChainToCalculateFinalAddress` | ExpressionGen.kt:1767 | Walks field chain, calculates offsets |
| `translateRegularAssignPointerIndexed` | AssignmentGen.kt:748 | Handles assignment to indexed pointer |

### How It Works

1. **Translate startpointer** (the base variable)
   - Gets pointer value into a register

2. **Traverse field chain** (`traverseRestOfDerefChainToCalculateFinalAddress`)
   - For each field: `LOADI WORD` to get next pointer
   - Calculates cumulative offset into struct

3. **Final load/store** (via LOADI/STOREI)
   ```
   ; Example: node.field.value
   LOADI WORD, r1, r0, offset_of_field   ; load from pointer+r0+offset
   ```

### IR Instructions Used

| Opcode | Used For |
|--------|---------|
| `LOADI` | Read from dereferenced pointer (+ offset) |
| `STOREI` | Write to dereferenced pointer (+ offset) |
| `LOADR` | Load from address in register |

### Does the Fix Affect IR Codegen?

**No.** The grammar fix happens **before** SimpleAST conversion:
- Grammar Parser → ANTLR parse tree
- Visitor (`Antlr2KotlinVisitor`) → CompilerAST (`PtrDereference`)
- CodeDesugarer → rewrites `ArrayIndexedPtrDereference`
- SimplifiedAstMaker → SimpleAST (`PtPointerDeref`)
- **IR codegen receives `PtPointerDeref` as before**

The IR codegen never sees the difference - it just gets `PtPointerDeref` with the chain.

### Implications

- No changes needed to `codeGenIntermediate` for this fix
- The `PtPointerDeref` API remains the same
- Existing tests should continue to pass

---

## 8. 6502 Codegen Analysis

### Overview

The 6502 code generator (`codeGenCpu6502`) processes `PtPointerDeref` to generate 6502 assembly.

### Key Functions

| Function | Location | Purpose |
|----------|----------|---------|
| `dereffunction` | PointerAssignmentsGen.kt:140 | Walks pointer chain, returns ZP var + offset |
| `assignPointerDerefExpression` | PointerAssignmentsGen.kt:244 | Assignment from/to pointer deref |

### How It Works (Lines 140-242)

1. **Initialize ZP scratch pointer** (`P8ZP_SCRATCH_PTR`)
   - If pointer is not in ZP, copy to scratch var

2. **Traverse field chain** (lines 218-225)
   ```
   ; For each field in chain:
   lda  P8ZP_SCRATCH_PTR    ; add field offset
   clc
   adc  #<fieldoffset
   sta  P8ZP_SCRATCH_PTR
   ; ... read 2-byte pointer from new location
   lda  (P8ZP_SCRATCH_PTR),y
   tax
   iny
   lda  (P8ZP_SCRATCH_PTR),y
   sta  P8ZP_SCRATCH_PTR+1
   stx  P8ZP_SCRATCH_PTR
   ```

3. **Final access** uses Y register for offset:
   ```
   lda  (P8ZP_SCRATCH_PTR),y   ; load byte
   sta  ...                  ; store
   ```

### Comparison with IR Codegen

| Aspect | IR Codegen | 6502 Codegen |
|--------|----------|------------|
| Pointer storage | CPU registers | ZP scratch var (`P8ZP_SCRATCH_PTR`) |
| Word load | Single `LOADI` | Two-byte read (`lda (ptr),y` / `iny` / `lda (ptr),y`) |
| Offset register | Any register | Y register |
| Field traversal | Loop with `LOADI` + field offset | Add offset + read pointer from memory |

### Does the Fix Affect 6502 Codegen?

**No.** Same as IR - codegen receives `PtPointerDeref` unchanged.

The pointer chain processing is identical regardless of whether original syntax had `^^`:
- `node.next[2].value = 123` → `PtPointerDeref(startpointer=node, chain=["next", "value"], derefLast=false)`
- `node^^.value = 123` → Same AST representation

### Implications

- No changes needed to `codeGenCpu6502`
- No changes to ZP scratch variable usage
- Existing tests continue to pass

### Important Note

The 6502 codegen uses ZP variables heavily.
The fix doesn't require additional ZP scratch usage.

---

## 9. Implementation Approach Options

### The Two Test Cases

| Test | Pattern | Currently |
|------|---------|----------|
| Test 1 (line 1443) | `l1.s[0] = 4242` and `l1^^.s[0] = 4242` | Parse error |
| Test 2 (line 1465) | `listarray[2].value = 123` | Parse error |

Both fail because `POINTER` (`^^`) is required in grammar rule.

---

### Option A: Minimal Fix (singlederef only)

**Change:** Make `POINTER` optional in `singlederef` rule.

```antlr
// Current (line 395)
singlederef : identifier arrayindex? POINTER ;

// Proposed
singlederef : identifier arrayindex? POINTER? ('.' identifier)?
          // Handles: var, var[0], var^^, var[0]^^, var.field, var[0].field
```

**What it fixes:**
- `l1.s[0] = 4242` → Works (Test 1)
- `listarray[2].value = 123` → Works (Test 2)

**Scope:** Single grammar rule change (THEORETICAL - not viable).

**Complexity:** Appears LOW but creates ambiguity issues.

**Risk:** HIGH - ambiguity with `scoped_identifier` causes parse failures.

**Visitor changes:** Not applicable - grammar change not viable.

**Codegen changes:** Not applicable.

### INVESTIGATION FINDING: Option A Is Not Viable

Attempting to implement Option A revealed fundamental issues:

1. **Ambiguity with `scoped_identifier`:** Both `pointerdereference` and `scoped_identifier` match `a.b`. ANTLR's prediction can't reliably choose between them.

2. **POINTER placement issue:** The `singlederef` rule `identifier arrayindex? POINTER` requires `^^` at the END. For `ptr^^.field`, the `.field` singlederef has no `POINTER`, causing parse failure.

3. **Greedy matching:** `scoped_identifier` greedily matches `ptr.field`, leaving array indices stranded.

**Conclusion:** The grammar fix requires Option B (comprehensive redesign).

---

---

### Option B: Comprehensive Redesign

**Change:** Restructure `.` as explicit postfix operator throughout grammar.

```antlr
dottedChain : primary (('.' identifier) | ('[' expression ']') | POINTER)* ;
primary    : identifier | directmemory | '(' expression ')' ;
```

**What it fixes:** All pointer dereference issues, clean grammar design.

**Scope:** Entire grammar restructuring.

**Complexity:** HIGH - requires:
- Grammar redesign
- AST changes (unify `PtrDereference` + `ArrayIndexedPtrDereference`)
- Visitor updates
- SymbolTable name lookup rewrites

**Risk:** HIGH - breaking changes to AST assumptions.

**Files affected:** ~10+ files across compilerAst, simpleAst.

---

### Recommendation

**Option A as originally described does NOT work.** Making `POINTER` optional in `singlederef` creates unresolvable ambiguity with `scoped_identifier` in ANTLR 4.

The actual fix requires **Option B (Comprehensive Redesign)** or a targeted intermediate approach:
1. Restructure `singlederef` to NOT require `POINTER` at the end
2. Handle `ptr^^.field` as: `singlederef(ptr^^)` + `.singlederef(field)` 
3. Resolve ambiguity by ensuring `scoped_identifier` and `pointerdereference` don't compete

**Status after investigation:** No grammar changes made. The current grammar is correct for what it supports. The skipped tests document a known limitation that requires deeper grammar work to fix.

**Current workaround:** Users must write explicit `^^` with array indexing: `ptr[idx]^^.field = value`

---

## 10. Related Files to Update

### Current Status (No Changes Made)

The grammar investigation revealed that Option A (simple fix) is not viable. The grammar remains unchanged.

| File | Status |
|------|--------|
| `parser/src/main/antlr/Prog8ANTLR.g4` | Unchanged (Option A not viable) |
| `compiler/test/TestPointers.kt` | Added 1 new test, 2 xtests remain skipped |
| `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt` | Unchanged |
| `compiler/src/prog8/compiler/astprocessing/CodeDesugarer.kt` | Unchanged |
