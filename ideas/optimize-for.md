# FOR Loop Codegen Optimizations

Actual size/speed optimizations for FOR loop codegen. Robustness-only changes
(bounds checks against loop variable modification) are not pursued: modifying
the loop variable is undefined behavior and the docs now say so.

All code is in `codeGenCpu6502/src/prog8/codegen/cpu6502/ForLoopsAsmGen.kt`.

The byte cases are already optimal (increment-then-compare, no `jmp`). The
word and long cases still use the older check-equality-then-increment pattern
with a trailing `jmp loop`; restructuring them to increment-then-compare
eliminates the `jmp` and shortens the code.

---

## Signed vs unsigned correctness

All proposed replacements use **equality comparisons** (`bne`/`beq` chains),
exactly like the current code. Equality is sign-agnostic, so the restructure
introduces no signed/unsigned behavioral difference. (This is in contrast to
the rejected `bcc`/`bcs` bounds checks, where signedness changes meaning.)

The one real hazard: the compare value is `last±1`, computed in Kotlin Int
arithmetic. It must wrap at the *loop variable's width* to stay correct:

- **Words (16-bit):** emission via `#<value` / `#>value` truncates to 16 bits,
  which matches the variable's wrap. E.g. signed descent to `last = -32768`:
  `last-1 = -32769` = `$FFFF7FFF`, and `<`/`>` extract `$FF`/`$7F` = `$7FFF`,
  which is exactly the value `dec` produces when wrapping `$8000` down. OK.
- **Longs (32-bit):** Kotlin Int is 32-bit two's complement, so `last±1`
  overflow wraps identically to the variable. E.g. `last = $7FFFFFFF` gives
  `last+1 = $80000000`; `toLongHex()` uses `toUInt()` so negatives render
  correctly. OK.
- **Zero crossings** (`-10 to 10`, `10 downto -10`): the cascading inc/dec
  handles the `$FFFF` ↔ `$0000` transition mid-range; equality compare is
  unaffected by sign. OK.

All of the above must be covered by tests (see below).

---

## 1. Simple Ascending Word Range, Step 1

**Function:** `translateForSimpleWordRangeAsc` (line 1445)

Current (25 bytes):
```asm
loop:
        ; body
        lda  w
        cmp  #<last
        bne  +
        lda  w+1
        cmp  #>last
        beq  end
+       inc  w
        bne  loop
        inc  w+1
        jmp  loop
end:
```

Proposed (22 bytes, -3):
```asm
loop:
        ; body
        inc  w
        bne  +
        inc  w+1
+       lda  w
        cmp  #<(last+1)
        bne  loop
        lda  w+1
        cmp  #>(last+1)
        bne  loop
end:
```

**Semantics:** identical iteration count; the compare just happens after the
increment instead of before, against `last+1` instead of `last`.

**Cycles (absolute):** common iteration identical (18). Wrap iterations
(every 256th) are 3 cycles faster (no `jmp`). Exit iteration is 8 cycles
slower (once). Net: 3 bytes smaller always; a few cycles slower for loops
shorter than ~300 iterations without wraps, slightly faster otherwise.

**Edge cases:** works for `range.last == 65535` (compares against `$0000`
after wrap; same code path serves signed `last == -1` since 65535 = `$FFFF`).
No special cases needed.

---

## 2. Simple Descending Word Range, Step -1, `range.last != 0`

**Function:** `translateForSimpleWordRangeDesc` (line 1473)

Current (28 bytes):
```asm
loop:
        ; body
        lda  w
        cmp  #<last
        bne  +
        lda  w+1
        cmp  #>last
        beq  end
+       lda  w
        bne  +
        dec  w+1
+       dec  w
        jmp  loop
end:
```

Proposed (25 bytes, -3):
```asm
loop:
        ; body
        lda  w
        bne  +
        dec  w+1
+       dec  w
        lda  w
        cmp  #<(last-1)
        bne  loop
        lda  w+1
        cmp  #>(last-1)
        bne  loop
end:
```

**Cycles (absolute):** common iteration 22 vs 25 (3 cycles faster - the
redundant second `lda w` and the `jmp` are gone). Wrap iterations 27 vs 30.
Exit iteration 10 cycles slower (once). Net faster for any loop with 5+
iterations, and 3 bytes smaller always.

**Edge cases:** keep the existing `range.last == 0` special case (checks
w==0 before decrementing; smaller than a unified compare against `$FFFF`).

---

## 3. Simple Ascending Long Range, Step 1

**Function:** `translateForSimpleLongRangeAsc` (line 1511)

Same restructure as case 1: move the 4-byte cascading `inc`/`bne` chain
before the compare, compare against `last+1` with a cascading `bne` chain,
drop the trailing `jmp loop`.

**Result:** 49 -> 46 bytes (-3), identical cycle count per iteration.

**Edge cases:** works for `range.last == $7FFFFFFF` (`last+1` overflows Int
to `$80000000`, which is exactly what the variable increments to).

---

## 4. Simple Descending Long Range, Step -1, `range.last != 0`

**Function:** `translateForSimpleLongRangeDesc` (line 1555)

Same restructure as case 2: move the 4-byte cascading decrement before the
compare, compare against `last-1`, drop the trailing `jmp loop`.

**Result:** 58 -> 55 bytes (-3), common iteration 3 cycles faster (22 vs 25).

**Edge cases:** keep the existing `range.last == 0` special case.
`range.last == $80000000` works: `last-1` overflows Int to `$7FFFFFFF`,
matching the variable's wrap.

---

## Summary

| Loop Form | Savings | Speed |
|---|---|---|
| Word asc step 1 | -3 bytes | same (slightly faster with wraps) |
| Word desc step -1, last!=0 | -3 bytes | 3 cycles/iter faster |
| Long asc step 1 | -3 bytes | same |
| Long desc step -1, last!=0 | -3 bytes | 3 cycles/iter faster |

## Testing Strategy

ksim65 execution tests per pattern verifying:
1. Correct iteration count, including single-iteration ranges
2. Correct values of the loop variable inside the body
3. Wrap of the low byte (ranges crossing `$xx00` boundaries)
4. Signed/unsigned boundary cases:
   - unsigned word ascending to 65535, descending to 0
   - signed word ascending to 32767, descending to -32768
   - signed word ranges crossing zero in both directions
   - long ascending to `$7FFFFFFF`, descending to `$80000000`
5. Both `word` (signed) and `uword` (unsigned) loop variable types
