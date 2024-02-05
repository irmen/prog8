TODO
====

while not cx16.mouse_pos()  should give error


ConstantFoldingOptimizer (after merging master):
   after(numLiteral..) :  check that cast to/from BOOL is not done??




===== ====== =======
VM    6502   what
===== ====== =======
ok    ok     boolean const
ok    ok     boolean variables value
ok    ok     static bool var (block scope) with initializer value (staticVariable2asm)
ok    ok     boolean arrays value, list and single value
ok    ok     type error for bool[3] derp = 99    and also for init value [1,0,1] and also for [true, false, 1, 0, 222]
ok    ok     return boolean value from sub
ok    .      make sure that and,or,xor,not aren't getting replaced by the bitwise versions
ok    .      and, or, xor, not work in expressions: print_ub((bb and true) as ubyte)
ok    .      logical not works, also inplace
ok    .      logical xor works, also inplace
-     .      efficient code for manipulating bools in an array (normal and agumented assigns)
ok    ok     bitwise logical ops on bools give type error, including invert
ok    ok     arithmetic ops on bools give type error
ok    ok     boolean values in ubyte array should give type error
ok    ok     while booleanvar==42    should give type error
ok    ok     do..until booleanvar==42    should give type error
ok    ok     while not <integervar>   should give type error
FAIL  .      while not <integer functioncall>   should give type error
ok    .      while boolean  should produce identical code as  while integer!=0
ok    .      while not guessed  -> can we get rid of the cmp?
ok    .      if someint==0 / ==1  should stil produce good asm same as what it used to be with if not someint/if someint
ok    .      if not X -> test all variations with and without else
yes   .      is this De Morgan's optimization still useful in this branch? :   not a1 or not a2 -> not(a1 and a2)  likewise for and.
yes   .      is it beneficial to somehow have DeMorgan's law also work on integer types   if b1==0 and b2==0 -> if (b1 & b2)==0
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

