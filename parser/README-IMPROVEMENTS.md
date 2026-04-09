# ANTLR4 Grammar Improvements for Prog8

## Overview

This document outlines recommended improvements to the `Prog8ANTLR.g4` grammar file, focusing on clearer parse errors, rule optimization, and maintainability.

---

# Language Design Inconsistencies

These are **not** ANTLR implementation issues - they are **language design inconsistencies** in the grammar itself that would confuse users and make the language harder to learn.

## 1. Inconsistent Statement Terminators

**Current Grammar:**
```antlr
statement_block : '{' EOL? (statement | EOL) * '}';
module: EOL* (module_element (EOL+ module_element)*)? EOL* EOF;
```

**Problem:** 
- Module elements **require** `EOL+` between them
- Statements inside blocks **do not require** EOL separation

This means:
```prog8
{ a=1 b=2 }     ; valid - no EOL between statements
{ a=1
  b=2 }         ; valid - with EOL
```

But at module level:
```prog8
block1 { } block2 { }   ; INVALID - needs EOL between blocks
```

**Recommendation:** Make EOL requirements consistent - either require EOL everywhere or make it optional everywhere.

---

## 2. Inconsistent Optional EOL Before Braces

**Current Grammar:**
```antlr
block: identifier integerliteral? EOL? '{' EOL? ...
statement_block : '{' EOL? ...
enum: ENUM identifier '{' EOL? ...
structdeclaration: STRUCT identifier '{' EOL? ...
```

**Problem:** Some constructs allow optional EOL before `{`, but there's no clear pattern. Why is it `EOL?` everywhere instead of consistent?

**Recommendation:** Either require EOL before `{` everywhere for consistency, or document why it varies.

---

## 3. Pointer Dereference Grammar is Admittedly "Cursed"

**Current Grammar:**
```antlr
pointerdereference: (prefix = scoped_identifier '.')? derefchain ('.' field = identifier)?;
derefchain: singlederef ('.' singlederef)*;
singlederef: identifier arrayindex? POINTER;
```

**Comment in grammar:**
> "This is a cursed mix of IdentifierReference and binary expressions with '.' dereference operators."

**Problems:**

1. **Ambiguity between field access and chained dereference**: The optional `('.' field = identifier)?` at the end conflicts with `'.' singlederef` inside `derefchain`. In `foo^^.bar^^`, ANTLR resolves this by rule order, but the grammar doesn't express intent clearly.

2. **Asymmetric prefixes**: `(prefix = scoped_identifier '.')?` treats the first identifier differently from the rest, even though semantically they're part of the same chain.

3. **Backward indexing**: `singlederef` supports `foo[0]^^` (index then dereference) but **not** `foo^^[0]` (dereference then index the result), which is a common operation in C-like languages.

4. **AST reconstruction is painful**: The grammar flattens everything into one node with a `prefix`, `derefchain`, and optional `field`. The Kotlin visitor essentially has to re-parse the grammar's output to figure out what the user actually wrote, leading to complex logic in the `Antlr2KotlinVisitor`.

5. **The grammar comment admits it**: *"This is a cursed mix of IdentifierReference and binary expressions with '.' dereference operators."*

**Recommendation:** This needs a proper redesign. Stop trying to enforce semantic structure in the grammar and instead parse the surface syntax — a sequence of identifiers, dots, brackets, and `^^` operators — and let the AST builder figure out the meaning.

```antlr
pointerderefchain: 
    primary 
    ( '[' expression ']'            // Array/pointer indexing
    | POINTER                       // Pointer dereference ^^
    | '.' identifier                // Field access
    )*
    ;

primary: 
    scoped_identifier               // Named variable/function
    | directmemory                  // @(expr)
    | '(' expression ')'            // Parenthesized
    ;
```

This treats `.` and `^^` and `[]` as postfix operators with equal precedence (left-associative), which is how they're actually used in practice. It removes the artificial distinction between "prefix", "chain", and "field" entirely.

**Caveat:** This simplification conflicts with `scoped_identifier` (defined as `identifier ('.' identifier)*`). If both exist, `foo.bar` becomes ambiguous: is it a scoped identifier or a pointer chain with a field access?
Resolving this requires either:
1. **Removing `scoped_identifier`**: Parse all chains as postfix operators and defer the "namespace vs field" decision to the **AST semantic analyzer**. This simplifies the grammar but moves significant complexity into the type checker.
2. **Strict syntactic separation**: Use different operators (e.g., `::` for namespaces), but this is a breaking language change.

This is why the fix is **High complexity** — it touches the semantic analysis phase, not just the ANTLR rules.

---

## 4. Trailing Commas Inconsistency

**Current Grammar:**
```antlr
enum: '{' EOL? enum_member? (',' EOL? enum_member)* ','? EOL? '}';  ; trailing comma allowed
arrayliteral: '[' EOL? expression? (',' EOL? expression)* ','? EOL? ']';  ; trailing comma allowed
```

**Problem:** Some constructs allow trailing commas, others don't:
- ✅ `enum` - trailing comma allowed
- ✅ `arrayliteral` - trailing comma allowed  
- ❌ Function parameters - no trailing comma shown
- ❌ Expression lists - no trailing comma shown
- ❌ Multi-assign targets - no trailing comma shown

**Recommendation:** Make trailing commas consistently allowed (or consistently disallowed) across all list constructs.

---

## Summary of Language Design Issues

**Note on evaluation criteria:** Issues are evaluated with Prog8's **retro 6502/BASIC target** in mind. Features that would be "archaic" in modern languages (like `ON...GOTO`) are **intentional and appropriate** for this platform. Issues listed here are about **internal consistency** and **clarity**, not about being "modern" vs "retro".

| Issue | Severity | Recommendation |
|-------|----------|----------------|
| Pointer dereference grammar | High | Proper redesign needed |
| Inconsistent EOL requirements | Medium | Make consistent |
| Trailing commas inconsistency | Low | Make consistent |

**Removed from list (retro-appropriate features):**
- `ON...GOTO` with `else` clause - **NOT a flaw**. Classic BASIC syntax appropriate for 6502 target. The `else` clause provides useful error handling for out-of-range indices (e.g., menu dispatch). Compiles efficiently to 6502 jump tables. See comment in `.g4` file.

**Removed from list (incorrect assessment):**
- `enum`/`alias` as statements - **NOT a flaw**. These are declaration statements like `vardecl` and can appear inside subroutines as local type definitions. See comment in `.g4` file.
- `VOID` in multiple contexts - **NOT a flaw**. `void = func()` is rejected with helpful error message "cannot assign to 'void', perhaps a void function call was intended". `void func()` is the correct syntax for discarding return values.
- `repeat` optional expression - **NOT a flaw**. Documentation clearly explains that `repeat { ... }` without expression is an infinite loop, while `repeat N { ... }` repeats N times. This is intentional design.

**Issues that may also be retro-appropriate (needs further review):**
- **Inconsistent EOL requirements** - May be intentional flexibility for different contexts (module vs block level)
- **Trailing commas inconsistency** - May reflect different use cases (enums/arrays are data, function calls are execution)

These should be evaluated based on **what makes sense for 6502 programmers** coming from BASIC/assembly, not modern language conventions.

---

# ANTLR4 Implementation Improvements

## Current Issues Summary

The Prog8 ANTLR4 grammar has several areas that could be improved:

1. **Generic Error Messages**: Users receive cryptic ANTLR default messages
2. **Manual Keyword Handling**: Must explicitly list keywords that can be identifiers
3. **"Cursed" Pointer Dereference**: Complex grammar that doesn't handle chaining well

---

## 2. Rule Optimization

### A. Statement Rule Grouping

**Current Problem**: 25 alternatives with no grouping, hard to extend (lines 98-124)

**Optimization**: Group by category:

```antlr
statement
    : directive
    | declaration
    | controlFlow
    | assignment
    | jumpStatement
    | loopStatement
    | subroutine
    | inlineasm
    | labeldef
    | alias
    ;

declaration
    : variabledeclaration
    | structdeclaration
    | subroutinedeclaration
    ;

controlFlow
    : if_stmt
    | branch_stmt
    | whenstmt
    | ongoto
    ;

jumpStatement
    : unconditionaljump
    | returnstmt
    | breakstmt
    | continuestmt
    ;

loopStatement
    : forloop
    | whileloop
    | untilloop
    | repeatloop
    | unrollloop
    ;
```

### C. Directive Rule Simplification

**Current Problem**: Complex, ambiguous alternatives (line 156)

**Optimization**: Simplify structure:

```antlr
directive
    : '%' name=UNICODEDNAME '!'? directiveArgs
    ;

directiveArgs
    : '(' EOL? scoped_identifier (',' EOL? scoped_identifier)* ','? EOL? ')'  // List
    | directiveArg (',' directiveArg)*  // Regular args
    |  // Empty
    ;
```

---

## 3. Lexer Status

**Assessment:** No meaningful improvements remain.

The lexer is in a stable, efficient state. Key points:

- **Float Numbers:** Already simplified to a single rule.
- **NOT_IN:** Attempted and failed. The current whitespace-dependent token is fragile but works; moving it to the parser creates ambiguity.
- **Numbers/Strings/Identifiers:** Standard and correct.
- **Keywords:** All necessary tokens.
- **Commented-out code:** The `// WS2 : '\\' EOL -> skip;` line is abandoned line-continuation logic and can be removed cosmetically.

There are no functional lexer improvements left to make.

---

## 4. Specific Problem Areas

### A. Pointer Dereference Cleanup

**Current Problem**: Described as "cursed mix" in comment, doesn't handle chaining well (lines 343-352)

**Current Grammar**:
```antlr
pointerdereference: (prefix=scoped_identifier '.')? derefchain ('.' field=identifier)? ;
derefchain: singlederef ('.' singlederef)* ;
singlederef: identifier arrayindex? POINTER ;
```

**Improved Structure**:
```antlr
pointerdereference
    : pointerBase ('.' pointerElement)*
    ;

pointerBase
    : scoped_identifier '.'?     // Optional base with dot
    |                           // Or start with dereference
    ;

pointerElement
    : identifier arrayindex? POINTER  // array^^ or just pointer^^
    | identifier                      // field access
    ;
```

### B. Assignment Rule Simplification

**Current Problem**: Multiple ambiguous alternatives (line 182)
```antlr
assignment
    : (assign_target '=' expression)
    | (assign_target '=' assignment)      // Chained assignment - problematic
    | (multi_assign_target '=' expression)
    ;
```

**Fix**: Remove chained assignment ambiguity:
```antlr
assignment
    : assign_target '=' expression
    | multi_assign_target '=' expression
    // Remove: assign_target '=' assignment  // Too ambiguous
    ;
```

### C. Module Rule EOL Handling

**Current Problem**: Complex EOL* patterns everywhere (line 79)
```antlr
module: EOL* (module_element (EOL+ module_element)*)? EOL* EOF;
```

**Note**: This is actually necessary due to comment/EOL interleaving (issue #47), but could be cleaner with a channel-based approach.

---

## 6. Implementation Priority

| Priority | Issue | Impact | Effort | Files to Modify |
|----------|-------|--------|--------|------------------|
| **Medium** | Statement rule grouping | Maintainability | Low | `Prog8ANTLR.g4` |
| **Medium** | Identifier keyword handling | Completeness | Low | `Prog8ANTLR.g4` |
| **Low** | Pointer dereference cleanup | Technical debt | Medium | `Prog8ANTLR.g4` |

---

## 8. Testing Strategy

After implementing these changes:

1. **Regression Testing**: Ensure all existing test cases still pass
2. **Error Message Testing**: Create test cases for each new error message
3. **Edge Case Testing**: Test complex expressions, pointer dereferencing, etc.
4. **Performance Testing**: Verify grammar changes don't significantly impact parsing speed

---

## 10. Related Files to Update

- `/home/irmen/Projects/prog8/parser/src/main/antlr/Prog8ANTLR.g4` - Main grammar file
- `/home/irmen/Projects/prog8/compilerAst/src/prog8/parser/Prog8Parser.kt` - Error handling
- Test files in `/home/irmen/Projects/prog8/compiler/test/` - Update for new error messages
- Documentation - Update language specification if grammar semantics change
