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

## 2. `enum` and `alias` as Statements (Semantically Wrong)

**Current Grammar:**
```antlr
block_statement: enum | alias;
statement: enum | alias;
```

**Problem:** Enums and aliases can be defined inside subroutine bodies:
```prog8
myblock {
    enum Status { OK, FAIL }    ; block level - makes sense
    
    sub foo() {
        enum Status2 { A, B }   ; inside subroutine - weird!
        alias x = y             ; also weird inside subroutine
    }
}
```

**Recommendation:** Remove `enum` and `alias` from the `statement` rule. They should only be allowed in `block_statement`.

---

## 3. Inconsistent Optional EOL Before Braces

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

## 4. `VOID` Used in Multiple Incompatible Contexts

**Current Grammar:**
```antlr
assign_target: VOID #VoidTarget;
functioncall_stmt: VOID? scoped_identifier '(' ...;
```

**Problem:** `VOID` is used as:
1. An assignment target (to discard return values): `void, x = func()`
2. A statement prefix (to discard return values): `void func()`

These are different use cases but use the same keyword. The grammar allows:
```prog8
void = func()      ; ??? Is this valid? What does this mean?
```

**Recommendation:** Consider different syntax for discarding return values in different contexts, or clarify the semantics.

---

## 5. `repeat` Loop Optional Expression - Unclear Semantics

**Current Grammar:**
```antlr
repeatloop: 'repeat' expression? EOL? (statement | statement_block);
unrollloop: 'unroll' expression EOL? (statement | statement_block);
```

**Problem:** 
- `repeat` has optional `expression?` - what does it mean when omitted?
- `unroll` **requires** an expression
- Why the inconsistency?

```prog8
repeat 5 { ... }      ; repeat 5 times?
repeat { ... }        ; repeat forever? What's the difference from 'while true'?
unroll 5 { ... }      ; must specify count
```

**Recommendation:** Either make the expression required for `repeat`, or document clearly what omission means. Make `repeat` and `unroll` consistent.

---

## 6. Pointer Dereference Grammar is Admittedly "Cursed"

**Current Grammar:**
```antlr
pointerdereference: (prefix = scoped_identifier '.')? derefchain ('.' field = identifier)?;
derefchain: singlederef ('.' singlederef)*;
singlederef: identifier arrayindex? POINTER;
```

**Comment in grammar:**
> "This is a cursed mix of IdentifierReference and binary expressions with '.' dereference operators."

**Problems:**
1. The grammar allows `foo.bar^^.baz` but what about `foo^^.bar`?
2. Array indexing on pointers: `ptr[0]^^` vs `ptr^^[0]` - which is valid?
3. The comment admits this is a hack that needs rewriting

**Recommendation:** This needs a proper redesign. Consider treating `^^` as a postfix operator with clear precedence rules.

---

## 7. `sizeof` Allows Arbitrary Expressions

**Current Grammar:**
```antlr
expression: 'sizeof' '(' sizeof_argument ')';
sizeof_argument: basedatatype | expression | pointertype;
```

**Problem:** You can do `sizeof(5)` (expression) but what does that mean? Shouldn't it be `sizeof(byte)` or `sizeof(myvar)` only?

```prog8
sizeof(5)           ; ??? Returns size of what type?
sizeof(3 + 4)       ; ??? 
sizeof(byte)        ; Makes sense
sizeof(myvar)       ; Makes sense
```

**Recommendation:** Restrict `sizeof_argument` to `basedatatype | scoped_identifier | pointertype` - remove arbitrary `expression`.

---

## 8. Trailing Commas Inconsistency

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

## 9. `const` Declaration - Optional Type is Confusing

**Current Grammar:**
```antlr
constdecl: 'const' datatype? identifierlist '=' expression;
```

**Problem:** `datatype?` is optional, but then how is this different from a regular variable declaration? What happens if you omit the type?

```prog8
const x = 5         ; What type is x? Inferred?
const byte y = 5    ; Explicit type
```

**Recommendation:** Either make `datatype` required, or document clearly how type inference works for constants.

---

## 10. `inline` Keyword Only for `asmsub`

**Current Grammar:**
```antlr
asmsubroutine: INLINE? 'asmsub' asmsub_decl ...
```

**Problem:** `inline` can only prefix `asmsub`, not regular `sub`. But the documentation mentions inline functions - are they different from inline assembly?

```prog8
inline asmsub foo() { ... }   ; Valid
inline sub bar() { ... }      ; Invalid - syntax error
```

**Recommendation:** Either allow `inline` on regular subroutines too, or rename to `inlineasm` to clarify it's assembly-specific.

---

## Summary of Language Design Issues

**Note on evaluation criteria:** Issues are evaluated with Prog8's **retro 6502/BASIC target** in mind. Features that would be "archaic" in modern languages (like `ON...GOTO`) are **intentional and appropriate** for this platform. Issues listed here are about **internal consistency** and **clarity**, not about being "modern" vs "retro".

| Issue | Severity | Recommendation |
|-------|----------|----------------|
| `enum`/`alias` as statements | High | Remove from `statement` rule |
| Pointer dereference grammar | High | Proper redesign needed |
| `repeat` optional expression | Medium | Clarify semantics or require expression |
| `sizeof` allows expressions | Medium | Restrict to types and identifiers |
| Inconsistent EOL requirements | Medium | Make consistent |
| Trailing commas inconsistency | Low | Make consistent |
| `const` optional type | Low | Require or document inference |
| `inline` only for `asmsub` | Low | Extend or rename |
| `VOID` in multiple contexts | Low | Clarify or differentiate |

**Removed from list (retro-appropriate features):**
- `ON...GOTO` with `else` clause - **NOT a flaw**. Classic BASIC syntax appropriate for 6502 target. The `else` clause provides useful error handling for out-of-range indices (e.g., menu dispatch). Compiles efficiently to 6502 jump tables. See comment in `.g4` file.

**Issues that may also be retro-appropriate (needs further review):**
- **Inconsistent EOL requirements** - May be intentional flexibility for different contexts (module vs block level)
- **`VOID` in multiple contexts** - May be intentional overloading for convenience (discard return value in different positions)
- **`inline` only for `asmsub`** - May be intentional distinction (inline assembly vs inline function expansion are different concepts)
- **Trailing commas inconsistency** - May reflect different use cases (enums/arrays are data, function calls are execution)

These should be evaluated based on **what makes sense for 6502 programmers** coming from BASIC/assembly, not modern language conventions.

---

# ANTLR4 Implementation Improvements

## Current Issues Summary

The Prog8 ANTLR4 grammar has several areas that could be improved:

1. **Poor Error Recovery**: Uses `BailErrorStrategy` that fails immediately on first error
2. **Generic Error Messages**: Users receive cryptic ANTLR default messages
3. **Complex Expression Rule**: 25+ alternatives in a single left-recursive rule
4. **Fragile Lexer Rules**: `NOT_IN` token depends on whitespace
5. **Manual Keyword Handling**: Must explicitly list keywords that can be identifiers
6. **"Cursed" Pointer Dereference**: Complex grammar that doesn't handle chaining well

---

## 1. Error Recovery & Reporting Improvements

### Current Issues
- **BailErrorStrategy**: Fails immediately on first error with no recovery
- **Generic error messages**: ANTLR default messages like "mismatched input" are unhelpful
- **No rule-specific error messages**: Users get cryptic errors for common mistakes

### Recommended Improvements

#### Add Error Alternatives with Custom Messages

```antlr
// Assignment rule with error detection
assignment
    : assign_target '=' expression
    | assign_target '=' assignment
    | multi_assign_target '=' expression
    | assign_target '=' expression '=' expression+  // ERROR: chained assignment
      { notifyErrorListeners("Cannot chain assignments. Use multiple statements instead."); }
    ;
```

#### Add Error Recovery Rules

```antlr
statement
    : // ... valid statements
    | 'if' expression statement  // Missing THEN or block
      { notifyErrorListeners("Expected 'then' or '{' after if condition"); }
    | 'for' identifier 'in'      // Missing expression
      { notifyErrorListeners("Expected expression after 'in'"); }
    | 'while' '}'                // Missing condition
      { notifyErrorListeners("Expected condition after 'while'"); }
    ;
```

#### Improve Error Messages for Common Mistakes

```antlr
vardecl
    : datatype (arrayindex | EMPTYARRAYSIG)? TAG* identifierlist
    | datatype EMPTYARRAYSG identifierlist
      { if (!$datatype.text.equals("ubyte") && !$datatype.text.equals("byte")) 
          notifyErrorListeners("Empty array syntax [] only valid for byte/ubyte types"); }
    ;
```

---

## 2. Rule Optimization

### A. Expression Rule Refactoring

**Current Problem**: Massive left-recursive rule with 25+ alternatives (lines 202-233)

**Optimization**: Group by precedence using sub-rules:

```antlr
expression
    : primaryExpression
    | expression postfixOperator
    | prefixOperator expression
    | expression multiplicativeOp expression
    | expression additiveOp expression
    | expression shiftOp expression
    | expression relationalOp expression
    | expression equalityOp expression
    | expression bitwiseAndOp expression
    | expression bitwiseXorOp expression
    | expression bitwiseOrOp expression
    | expression rangeOp expression
    | expression 'in' expression
    | expression 'not' 'in' expression
    | expression 'and' expression
    | expression 'or' expression
    | expression 'xor' expression
    | 'if' expression 'then' expression 'else' expression  // if-expression
    ;

// Operator groups
multiplicativeOp: '*' | '/' | '%' ;
additiveOp: '+' | '-' ;
shiftOp: '<<' | '>>' ;
relationalOp: '<' | '>' | '<=' | '>=' ;
equalityOp: '==' | '!=' ;
bitwiseAndOp: '&' ;
bitwiseXorOp: '^' ;
bitwiseOrOp: '|' ;
rangeOp: 'to' | 'downto' ;
postfixOperator: '++' | '--' ;
prefixOperator: '+' | '-' | '~' ;
```

### B. Statement Rule Grouping

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

## 3. Lexer Improvements

### A. NOT_IN Token Fix

**Current Problem**: Fragile lexer rule with whitespace dependence (line 72)
```antlr
NOT_IN: 'not' [ \t]+ 'in' [ \t] ;
```

**Fix**: Move to parser rule:
```antlr
// Remove NOT_IN from lexer
// In parser:
expression
    : ...
    | left=expression 'not' 'in' right=expression  #NotInExpression
    ;
```

> **ATTEMPTED AND FAILED (2026-03-28)**: Multiple approaches were tried to fix this:
> - Moving `not in` to a parser rule alternative using `'not' 'in'` sequence
> - Placing the alternative at different positions in the expression rule (before/after 'in', at top)
> - All approaches failed because ANTLR's lexer always tokenizes `not` and `in` as separate tokens first
> - The parser rule approach creates ambiguity that prevents parsing `not in` correctly
> - The original lexer rule with token `NOT_IN` is fragile but works; any fix attempt causes more problems than it solves
> - **Conclusion**: Leave the lexer rule as-is; the fragility is acceptable

### B. Identifier Rule Improvement

**Current Problem**: Must manually list keywords that can be identifiers (line 266)
```antlr
identifier: UNICODEDNAME | UNDERSCORENAME | ON | CALL | INLINE | STEP ;
```

**Fix**: Use parser rule approach:
```antlr
// Instead of tokens, use a parser rule that matches any keyword as identifier
identifier
    : UNICODEDNAME
    | UNDERSCORENAME
    | keywordAsIdentifier
    ;

keywordAsIdentifier
    : 'on' | 'call' | 'inline' | 'step' | 'else' | 'then' | 'goto' | 'void' | 'struct'
    ;
```

> **ATTEMPTED AND FAILED (2026-03-28)**: This approach was tried but creates severe parsing ambiguity.
> - Adding many keywords as alternatives in a parser rule creates ambiguity with other grammar rules
> - The parser cannot distinguish between a keyword used as an identifier vs. as actual syntax
> - Tests showed ~300+ failures due to parse errors in various contexts (function calls, expressions, etc.)
> - The manual token listing approach is fragile but works correctly
> - **Conclusion**: Keep the current approach of explicitly listing keywords that can be used as identifiers

### C. Float Number Rules Simplification

**Current Problem**: Complex, potentially ambiguous rules (lines 51-54)
```antlr
FLOAT_NUMBER : FNUMBER (('E'|'e') ('+' | '-')? DEC_INTEGER)? ;
FNUMBER : FDOTNUMBER | FNUMDOTNUMBER ;
FDOTNUMBER : '.' (DEC_DIGIT | '_')+ ;
FNUMDOTNUMBER : DEC_DIGIT (DEC_DIGIT | '_')* FDOTNUMBER? ;
```

**Fix**: Simplify:
```antlr
FLOAT_NUMBER
    : DEC_DIGIT (DEC_DIGIT | '_')* ('.' (DEC_DIGIT | '_')*)?
      (('E'|'e') ('+'|'-')? DEC_INTEGER)?
    | '.' (DEC_DIGIT | '_')+ (('E'|'e') ('+'|'-')? DEC_INTEGER)?
    ;
```

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

## 5. Missing Syntactic Validations

Add parser-level validations to catch errors early:

```antlr
// Array type validation
vardecl
    : datatype (arrayindex | EMPTYARRAYSIG)? TAG* identifierlist
    | datatype EMPTYARRAYSIG identifierlist
      { if (!$datatype.text.equals("ubyte") && !$datatype.text.equals("byte")) 
          notifyErrorListeners("Empty array syntax [] only valid for byte/ubyte types"); }
    ;

// Function call validation
functioncall_stmt
    : VOID? scoped_identifier '(' EOL? expression_list? EOL? ')'
    | VOID? scoped_identifier '(' EOL? expression_list? EOL? ')' '=' expression  // ERROR: assignment in function call
      { notifyErrorListeners("Cannot assign to function call. Use separate statement."); }
    ;
```

---

## 6. Implementation Priority

| Priority | Issue | Impact | Effort | Files to Modify |
|----------|-------|--------|--------|------------------|
| **High** | Expression rule refactoring | Maintainability, error quality | Medium | `Prog8ANTLR.g4` |
| **High** | Add error recovery alternatives | User experience | Low | `Prog8ANTLR.g4` |
| **Medium** | NOT_IN to parser rule | Robustness | Low | `Prog8ANTLR.g4` |
| **Medium** | Statement rule grouping | Maintainability | Low | `Prog8ANTLR.g4` |
| **Medium** | Identifier keyword handling | Completeness | Low | `Prog8ANTLR.g4` |
| **Low** | Pointer dereference cleanup | Technical debt | Medium | `Prog8ANTLR.g4` |
| **Low** | Float number simplification | Maintainability | Low | `Prog8ANTLR.g4` |

---

## 7. Example: Improved Error Messages

### Before (Current)
```
line 5:8 mismatched input '=' expecting {<EOF>, EOL, ';', ...}
line 10:3 mismatched input 'if' expecting {'{', 'then', ...}
line 15:12 mismatched input 'on' expecting {UNICODEDNAME, ...}
```

### After (With Custom Messages)
```
line 5:8 syntax error: Cannot chain assignments. Use multiple statements instead.
line 10:3 syntax error: 'if' statement missing 'then' or '{' before condition body
line 15:12 syntax error: Variable 'on' is a keyword. Use a different name or escape it.
line 20:4 syntax error: Empty array syntax [] only valid for byte/ubyte types.
```

---

## 8. Testing Strategy

After implementing these changes:

1. **Regression Testing**: Ensure all existing test cases still pass
2. **Error Message Testing**: Create test cases for each new error message
3. **Edge Case Testing**: Test complex expressions, pointer dereferencing, etc.
4. **Performance Testing**: Verify grammar changes don't significantly impact parsing speed

---

## 9. Migration Plan

1. **Phase 1**: Implement high-priority error recovery improvements
2. **Phase 2**: Refactor expression and statement rules
3. **Phase 3**: Fix lexer issues (NOT_IN, identifier handling)
4. **Phase 4**: Clean up specific problem areas (pointers, assignments)
5. **Phase 5**: Add comprehensive error validations

Each phase should include:
- Grammar changes
- Test updates
- Documentation updates
- Performance verification

---

## 10. Related Files to Update

- `/home/irmen/Projects/prog8/parser/src/main/antlr/Prog8ANTLR.g4` - Main grammar file
- `/home/irmen/Projects/prog8/compilerAst/src/prog8/parser/Prog8Parser.kt` - Error handling
- Test files in `/home/irmen/Projects/prog8/compiler/test/` - Update for new error messages
- Documentation - Update language specification if grammar semantics change

---

## Conclusion

These improvements will significantly enhance the Prog8 parser by:
- Providing clearer, more helpful error messages
- Making the grammar more maintainable and extensible
- Fixing known parsing issues and ambiguities
- Improving overall user experience for developers using the language

The changes are designed to be backward-compatible where possible, with careful attention to maintaining existing functionality while improving the parser's robustness and usability.

---

# Appendix: BailErrorStrategy Migration Guide

## What is BailErrorStrategy?

`BailErrorStrategy` is an ANTLR4 error handling strategy that **immediately stops parsing** when it encounters the first syntax error, rather than attempting to recover and continue parsing.

### Key Characteristics:

1. **Fail-Fast**: Stops on the first error - no recovery attempts
2. **No Error Recovery**: Doesn't try to skip tokens or resynchronize
3. **Throws Exceptions**: Immediately throws `InputMismatchException` or other parse errors
4. **Simple but Limited**: Easy to implement but poor user experience

### Current Implementation in Prog8:

```kotlin
// File: /home/irmen/Projects/prog8/compilerAst/src/prog8/parser/Prog8Parser.kt
private object Prog8ErrorStrategy: BailErrorStrategy() {
    override fun recover(recognizer: Parser?, e: RecognitionException?) {
        fillIn(e, recognizer!!.context)
        reportError(recognizer, e)
    }
    
    override fun recoverInline(recognizer: Parser?): Token {
        val e = InputMismatchException(recognizer)
        fillIn(e, recognizer!!.context)
        reportError(recognizer, e)
        throw e
    }
}
```

## Problems with BailErrorStrategy:

1. **Single Error Only**: Users only see the first syntax error, not all issues
2. **Poor IDE Integration**: IDEs can't highlight multiple errors simultaneously
3. **Frustrating Workflow**: Fix one error, recompile, find next error
4. **Limited Context**: No information about what might be expected

---

## Migration Plan: Replace BailErrorStrategy

### 1. **Replace Error Strategy**

**Current:**
```kotlin
parser.errorHandler = Prog8ErrorStrategy     // BailErrorStrategy
```

**New:**
```kotlin
parser.errorHandler = DefaultErrorStrategy()  // Built-in recovery strategy
```

### 2. **Implement Custom Error Listener**

**Create new error listener:**
```kotlin
private class Prog8ErrorListener(val src: SourceCode): BaseErrorListener() {
    private val errors = mutableListOf<ParseError>()
    
    override fun syntaxError(recognizer: Recognizer<*, *>?, 
                            offendingSymbol: Any?, 
                            line: Int, 
                            charPositionInLine: Int, 
                            msg: String, 
                            e: RecognitionException?) {
        // Collect errors instead of throwing immediately
        val error = ParseError(msg, Position(src.origin, line, charPositionInLine+1, charPositionInLine+1), e ?: RuntimeException("parse error"))
        errors.add(error)
    }
    
    fun getErrors(): List<ParseError> = errors.toList()
    fun hasErrors(): Boolean = errors.isNotEmpty()
}
```

### 3. **Update Parser Setup**

**Current:**
```kotlin
fun parseModule(src: SourceCode): Module {
    val antlrErrorListener = AntlrErrorListener(src)
    val lexer = Prog8ANTLRLexer(CharStreams.fromString(src.text, src.origin))
    lexer.removeErrorListeners()
    lexer.addErrorListener(antlrErrorListener)
    val tokens = CommonTokenStream(lexer)
    val parser = Prog8ANTLRParser(tokens)
    parser.errorHandler = Prog8ErrorStrategy     // BailErrorStrategy
    parser.removeErrorListeners()
    parser.addErrorListener(antlrErrorListener)
    
    val parseTree = parser.module()
    // ... visitor pattern
}
```

**New:**
```kotlin
fun parseModule(src: SourceCode): Module {
    val errorListener = Prog8ErrorListener(src)
    val lexer = Prog8ANTLRLexer(CharStreams.fromString(src.text, src.origin))
    lexer.removeErrorListeners()
    lexer.addErrorListener(errorListener)
    val tokens = CommonTokenStream(lexer)
    val parser = Prog8ANTLRParser(tokens)
    parser.errorHandler = DefaultErrorStrategy()  // Recovery strategy
    parser.removeErrorListeners()
    parser.addErrorListener(errorListener)
    
    val parseTree = parser.module()
    
    // Check for errors after parsing
    if (errorListener.hasErrors()) {
        throw MultipleParseErrors(errorListener.getErrors())
    }
    
    // ... visitor pattern
}
```

### 4. **Create Multiple Errors Exception**

```kotlin
class MultipleParseErrors(val errors: List<ParseError>) : Exception() {
    override val message: String
        get() = "Found ${errors.size} parse errors:\n" + 
                errors.joinToString("\n") { "${it.position}: ${it.message}" }
}
```

### 5. **Update Compiler Error Handling**

**Current in Compiler.kt:**
```kotlin
} catch (px: ParseError) {
    args.errors.printSingleError("${px.position.toClickableStr()} parse error: ${px.message}".trim())
}
```

**New:**
```kotlin
} catch (mpe: MultipleParseErrors) {
    // Report all parse errors
    mpe.errors.forEach { error ->
        args.errors.printSingleError("${error.position.toClickableStr()} parse error: ${error.message}".trim())
    }
} catch (px: ParseError) {
    // Fallback for single errors
    args.errors.printSingleError("${px.position.toClickableStr()} parse error: ${px.message}".trim())
}
```

---

## Grammar Changes for Better Error Recovery

### 1. **Add Error Recovery Alternatives**

```antlr
// Statement with error recovery
statement
    : directive
    | ongoto
    | variabledeclaration
    | structdeclaration
    | assignment
    | augassignment
    | unconditionaljump
    | postincrdecr
    | functioncall_stmt
    | if_stmt
    | branch_stmt
    | subroutinedeclaration
    | inlineasm
    | returnstmt
    | forloop
    | whileloop
    | untilloop
    | repeatloop
    | unrollloop
    | whenstmt
    | breakstmt
    | continuestmt
    | labeldef
    | defer
    | alias
    // Error recovery alternatives
    | 'if' expression error=statement
      { notifyErrorListeners("Expected 'then' or '{' after if condition"); }
    | 'for' identifier 'in' error=statement
      { notifyErrorListeners("Expected expression after 'in'"); }
    | 'while' error=statement
      { notifyErrorListeners("Expected condition after 'while'"); }
    | 'return' error=expression
      { notifyErrorListeners("Invalid return expression"); }
    ;
```

### 2. **Add Synchronization Points**

```antlr
// Block with synchronization
block: identifier integerliteral? EOL? '{' EOL? (block_statement | EOL)* '}' 
    | identifier integerliteral? EOL? '{' error=EOL? (block_statement | EOL)* '}'
      { notifyErrorListeners("Error in block: " + $error.text); }
    ;

// Module with synchronization
module: EOL* (module_element (EOL+ module_element)*)? EOL* EOF
      | EOL* module_element error=EOL+ module_element* EOL* EOF
      { notifyErrorListeners("Error between module elements"); }
      ;
```

### 3. **Add Error Tokens**

```antlr
// Add to lexer
ERROR_TOKEN: . -> skip ;  // Skip unknown tokens
```

---

## Benefits of Migration

### 1. **Multiple Error Reporting**
- Users see all syntax errors at once
- Better IDE integration with multiple error highlights
- More efficient development workflow

### 2. **Better Error Context**
- ANTLR's recovery provides context about expected tokens
- Can suggest alternatives based on grammar
- More precise error locations

### 3. **Improved User Experience**
- Less frustrating compilation process
- Better error messages with context
- Ability to fix multiple issues in one iteration

---

## Implementation Steps

### Phase 1: Basic Migration
1. Replace `BailErrorStrategy` with `DefaultErrorStrategy`
2. Update error listener to collect instead of throw
3. Create `MultipleParseErrors` exception
4. Update compiler error handling

### Phase 2: Grammar Improvements
1. Add error recovery alternatives to key rules
2. Add synchronization points for better recovery
3. Improve error messages with context

### Phase 3: Advanced Features
1. Add error suggestion logic
2. Implement custom recovery strategies for specific patterns
3. Add error severity levels (warning vs error)

---

## Testing Strategy

### 1. **Create Test Cases with Multiple Errors**
```kotlin
test("multiple parse errors") {
    val src = """
        sub main() {
            x = 1 +    // Missing right operand
            if x > 0   // Missing then/block
            y =        // Missing expression
        }
    """
    // Should report all 3 errors, not just the first
}
```

### 2. **Test Error Recovery**
```kotlin
test("error recovery continues parsing") {
    val src = """
        sub bad() { x = 1 + }
        sub good() { return 42 }
    """
    // Should parse both subroutines and report error in first
}
```

### 3. **Test Synchronization**
```kotlin
test("block synchronization") {
    val src = """
        block1 {
            x = 1 +    // Error in block1
        }
        block2 {      // Should still parse block2
            y = 2
        }
    """
    // Should recover and parse block2 correctly
}
```

---

## Potential Challenges

### 1. **Cascading Errors**
- One syntax error might cause multiple subsequent errors
- Need to filter or prioritize errors intelligently

### 2. **Performance Impact**
- Error recovery has overhead
- Need to benchmark parsing performance

### 3. **False Positives**
- Recovery might parse invalid constructs
- Need to validate AST after parsing

---

## Summary

Migrating from `BailErrorStrategy` to a recovery-based approach will:

1. **Improve User Experience**: Show all errors at once instead of one-by-one
2. **Better IDE Integration**: Enable multiple error highlights
3. **Provide Context**: Better error messages with expected tokens
4. **Maintain Robustness**: Still catch all errors, just report them differently

The migration requires changes to:
- Error handling strategy in parser setup
- Error listener implementation  
- Compiler error reporting
- Grammar for better recovery
- Test cases for multiple errors

This change will significantly improve the development experience for Prog8 programmers while maintaining the compiler's accuracy and robustness.