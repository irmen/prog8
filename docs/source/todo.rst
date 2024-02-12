TODO
====

===== ====== =======
VM    6502   what
===== ====== =======
ok    .      boolean const
ok    .      boolean variables value, boolean subroutine param
ok    .      static bool var (block scope) with initializer value (staticVariable2asm)
ok    .      boolean arrays value, list and single value
ok    .      return boolean value from sub
ok    .      logical not, and, or, xor work correctly, also inplace
ok    .      make sure that and,or,xor,not aren't getting replaced by the bitwise versions in the Ast
ok    .      and, or, xor, not should work in expressions: print_ub((bb and true) as ubyte)
ok    .      bitwise logical ops on bools give type error, including invert
ok    .      arithmetic ops on bools give type error
ok    .      logical ops on ints give type error
ok    .      boolean values in ubyte array should give type error
ok    .      type error for bool[3] derp = 99    and also for init value [1,0,1] and also for [true, false, 1, 0, 222]
ok    .      while booleanvar==42  and   do..until booleanvar==42    should give type error
ok    .      while not <integervar>   should give type error
ok    .      while not <integer functioncall>   should give type error
ok    .      while not cx16.mouse_pos()  should give condition type error
ok    .      while boolean  should produce identical code as  while integer!=0
ok    .      while not boolvar  -> can we get rid of the cmp? (6502 only?)
ok    .      if someint==0 / ==1  should stil produce good asm same as what it used to be with if not someint/if someint
ok    .      efficient code for manipulating bools in an array (normal and agumented assigns)
ok    .      testmonogfx works
ok    .      check program sizes vs. master branch
===== ====== =======


check that the flood fill routine in gfx2 and paint still works.
re-allow typecast of const true/false back to ubytes 1 and 0.
re-allow typecast of const ubyte 0/1 to false/true boolean.


boolean trick to go from a compare >= value, to a bool
    cmp #value
	rol  a
	and  #1


IR: add TEST instruction to test memory content and set N/Z flags, without affecting any register.
    replace all LOADM+CMPI #0  / LOAD #0+LOADM+CMP+BRANCH   by this instruction

