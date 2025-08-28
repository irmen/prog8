TODO
====

this doesn't work but probably should:
        ^^uword xpositions_ptr
        repeat num_sprites {
            pokew(cx16.VERA_DATA0, xpositions_ptr^^)        ; must set data0 and data1 at the same time
            xpositions_ptr ++
        }


STRUCTS and TYPED POINTERS (6502 codegen specific)
--------------------------------------------------

- implement the TODO's in PointerAssignmentsGen.
- fix code size regressions (if any left)
- update structpointers.rst docs with 6502 specific things?
- optimize deref()  to not always add the field offset to the pointer value but using it in the Y register instead
- optimize deref()  when field offset is 0, don't use a temporary at all if the var is in zp already   (maybe already solved by the previous one?)
- optimize the multiplications in assignAddressOfIndexedPointer()
- optimize the float copying in assignIndexedPointer() (also word?)
- implement some more struct instance assignments (via memcopy) in CodeDesugarer (see the TODO) (add to documentation as well, paragraph 'Structs')
- try to optimize pointer arithmetic used in peek/poke a bit more so the routines in sorting module can use typed pointers without increasing code size, see test.p8 in commit d394dc1e
- should @(wordpointer) be equivalent to wordpointer^^ (that would require a LOT of code rewrite that now knows that @() is strictly byte based) ?
  or do an implicit cast @(wpointer as ubyte^^)  ?  And/or add a warning about that?
- add struct and pointer benchamrk to benchmark program?


OTHER
-----

not all source lines are correctly reported in the IR file,
for example the below subroutine only shows the sub() line:
        sub two() {
            cx16.r0 = peekw(ww + cx16.r0L * 2)
        }

and for example the below code omits line 5:
[examples/test.p8: line 4 col 6-8]  sub start() {
[examples/test.p8: line 6 col 10-13]  cx16.r2 = select2()
[examples/test.p8: line 7 col 10-13]  cx16.r3 = select3()
[examples/test.p8: line 8 col 10-13]  cx16.r4 = select4()
[examples/test.p8: line 9 col 10-13]  cx16.r5 = select5()


%option enable_floats

main {
    sub start() {
        cx16.r1 = select1()
        cx16.r2 = select2()
        cx16.r3 = select3()
        cx16.r4 = select4()
        cx16.r5 = select5()
    }

    sub select1() -> uword {
        cx16.r0L++
        return 2000
    }

    sub select2() -> str {
        cx16.r0L++
        return 2000
    }

    sub select3() -> ^^ubyte {
        cx16.r0L++
        return 2000
    }

    sub select4() -> ^^bool {
        cx16.r0L++
        return 2000
    }

    sub select5() -> ^^float {
        cx16.r0L++
        return 2000
    }
}
