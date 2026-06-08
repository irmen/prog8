# Error Handling Design Ideas

## Problem

Prog8 routines return `bool` (true=success, false=failure) for error signaling. Callers must nest `if` statements to check each result, creating "arrow code":

```prog8
if diskio.f_open(filename) {
    if read_header() {
        if parse_header() {
            ; success
        } else { error() }
    } else { error() }
} else { error() }
}
```

This is verbose, error-prone, and obscures the main logic.

## Proposed Solution: Zig-Inspired Error Handling

Borrow Zig's approach, adapted for Prog8's simplicity and 8-bit targets.

### Core Concept: `try` Keyword

`try` propagates the false case up the call stack (equivalent to Zig's `try`):

```prog8
sub loadFile(str filename, uword buffer) -> bool {
    try diskio.f_open(filename)    ; if false, return false
    try diskio.f_read(buffer)      ; if false, return false
    return true                    ; only reaches here if all succeeded
}
```

**Before:**
```prog8
sub loadFile(str filename, uword buffer) -> bool {
    if diskio.f_open(filename) {
        if diskio.f_read(buffer) {
            return true
        } else {
            return false
        }
    } else {
        return false
    }
}
```

### Error Handling with `catch`

For explicit error handling at the call site:

```prog8
if diskio.f_open(filename) |ok| {
    ; success path
} else |err| {
    txt.print("error opening file\n")
}
```

Or with a default value (like Zig's `catch` with value):

```prog8
const data = try readFile("data.bin") catch 0
```

### Cleanup: `defer` + `errdefer`

`defer` runs on all exit paths. `errdefer` runs only on error paths.

```prog8
sub processFile(str filename) -> bool {
    defer diskio.f_close()              ; always runs
    errdefer txt.print("failed\n")     ; only on error path

    try diskio.f_open(filename)
    try diskio.f_read(buffer)
    return true
}
```

**Execution paths:**

| Path | defer runs | errdefer runs |
|------|------------|---------------|
| Success | yes | no |
| Error | yes | yes |

## How `try` Interacts with Existing `defer`

`try` desugars to invoke defers before returning false:

```prog8
try diskio.f_open(filename)
```

Becomes:

```prog8
if not diskio.f_open(filename) {
    prog8_invoke_defers()     ; run all defers (LIFO)
    prog8_invoke_errdefers()  ; run all errdefers
    return false              ; propagate error
}
```

## Compiler Implementation Notes

### Current `defer` Implementation (DeferProcessor.kt)

- Desugars `defer` into a bitmask + handler
- Max 8 defers per subroutine (UBYTE bitmask)
- `prog8_invoke_defers` generated per subroutine
- Handles return value preservation via push/pop

### Extensions Needed

1. **Parser:** Add `try` and `errdefer` keywords to grammar
2. **AST:** New `TryExpression` and `ErrDefer` nodes
3. **DeferProcessor:** Extend to handle errdefer bitmask (separate from defer)
4. **Error propagation:** Insert defer/errdefer invocation before return false

### Bitmask Considerations

- Current limit: 8 defers per subroutine (UBYTE)
- Options:
  - Keep 8 + 8 (8 defers, 8 errdefers) - simple
  - Extend to 16 or 32 bits - more flexible
  - Use separate mask variables per subroutine

## Example: File I/O Module

**Before:**
```prog8
sub f_open(str filename) -> bool {
    ; ... implementation ...
    if cbm.READST()==0 {
        iteration_in_progress = true
        return true
    }
    f_close()
    cbm.CLOSE(READ_IO_CHANNEL)
    return false
}
```

**After:**
```prog8
sub f_open(str filename) -> bool {
    defer { cbm.CLRCHN() }
    errdefer { f_close(); cbm.CLOSE(READ_IO_CHANNEL) }

    cbm.SETNAM(strings.length(filename), filename)
    cbm.SETLFS(READ_IO_CHANNEL, drivenumber, READ_IO_CHANNEL)
    void cbm.OPEN()

    try reset_read_channel()
    try check_read_status()

    iteration_in_progress = true
    void cbm.CHRIN()
    try check_eof_status()

    cbm.CLOSE(READ_IO_CHANNEL)
    void cbm.OPEN()
    return true
}
```

## Design Decisions

| Question | Recommended |
|----------|-------------|
| Max defers per sub | 8 + 8 (separate masks) |
| `try` in void subs | Compile error - can't propagate |
| Error propagation | Always `return false` initially |
| Future: custom error types | Could add later if needed |

## References

- Zig error handling: https://zig.guide/language-basics/errors/
- Current defer implementation: `compiler/src/prog8/compiler/astprocessing/DeferProcessor.kt`
