TODO
====

fix compiler crash (virtual) for   ^^List @shared lp  ;   ^^List @shared temp = lp[2]
fix invalid pointer arithmetic in 6502 code for &&lp[2]  (it adds 2 to the pointer itself rather than the value of the pointer, and 2 is wrong as well)


add IR peephole optimization for mul / div by factor of 2 -> bitshift


STRUCTS and TYPED POINTERS (6502 codegen specific)
--------------------------------------------------

- implement the TODO's in PointerAssignmentsGen. (mostly related to an array indexed pointer assignment target)
- 6502 codegen should warn about writing to initialized struct instances when using romable code, like with arrays "can only be used as read-only in ROMable code"
- scan through 6502 library modules to change untyped uword pointers to typed pointers
- scan through 6502 examples to change untyped uword pointers to typed pointers
- fix code size regressions (if any left)
- update structpointers.rst docs with 6502 specific things?
- optimize deref()  to not always add the field offset to the pointer value but using it in the Y register instead
- optimize deref()  when field offset is 0, don't use a temporary at all if the var is in zp already   (maybe already solved by the previous one?)
- optimize the multiplications in assignAddressOfIndexedPointer()
- optimize the float copying in assignIndexedPointer() (also word?)
