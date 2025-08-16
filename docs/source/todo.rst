TODO
====

STRUCTS and TYPED POINTERS (6502 codegen specific)
--------------------------------------------------

- implement the TODO's in PointerAssignmentsGen. (mostly related to an array indexed pointer assignment target)
- 6502 codegen should warn about writing to initialized struct instances when using romable code, like with arrays "can only be used as read-only in ROMable code"
- 6502 asm symbol name prefixing should work for dereferences and structs too.
- scan through 6502 library modules to change untyped uword pointers to typed pointers
- scan through 6502 examples to change untyped uword pointers to typed pointers
- fix code size regressions (if any left)
- update structpointers.rst docs with 6502 specific things?
