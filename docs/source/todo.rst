TODO
====

fix 6502 code gen errors for the address-of (assignAddressOfIndexedPointer)
fix 6502 code gen errors that make some of the pointer examples crash. Important to do this before building more on wrong code!

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
