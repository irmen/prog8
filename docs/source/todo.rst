TODO
====

pointer arithmetic precedence issue?:
    ^^uword values
    cx16.r0 = values + (pos-1)      ; different outcome as:
    cx16.r0 = values + pos -1

sorting.gnomesort_uw()   : when converted to ^^uword, the resulting code is MUCH larger than before  (peek/poke code gen problem?)
(same with shellsort_uw and others)

this doesn't work:
        ^^uword xpositions_ptr
        repeat num_sprites {
            pokew(cx16.VERA_DATA0, xpositions_ptr^^)        ; must set data0 and data1 at the same time
            xpositions_ptr ++
        }


STRUCTS and TYPED POINTERS (6502 codegen specific)
--------------------------------------------------

- implement the TODO's in PointerAssignmentsGen.
- scan through 6502 examples to change untyped uword pointers to typed pointers
- fix code size regressions (if any left)
- update structpointers.rst docs with 6502 specific things?
- optimize deref()  to not always add the field offset to the pointer value but using it in the Y register instead
- optimize deref()  when field offset is 0, don't use a temporary at all if the var is in zp already   (maybe already solved by the previous one?)
- optimize the multiplications in assignAddressOfIndexedPointer()
- optimize the float copying in assignIndexedPointer() (also word?)
- implement some more struct instance assignments (via memcopy) in CodeDesugarer (see the TODO) (add to documentation as well, paragraph 'Structs')
- try to optimize pointer arithmetic used in peek/poke a bit more so the routines in sorting module can use typed pointers without increasing code size
