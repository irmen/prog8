TODO
====

examples/maze is larger than on 10.1
rockrunner is a lot bigger still than on 10.1
paint is bigger than on 10.1

cx16/testmonogfx is broken
assembler is broken
imageviewer is broken
paint flood fill is broken
rockrunner load caveset list is broken
medemo and dsdemo are broken



===== ====== =======
VM    6502   what
===== ====== =======
ok    ok     boolean const
ok    ok     boolean variables value, boolean subroutine param
ok    ok     static bool var (block scope) with initializer value (staticVariable2asm)
ok    ok     boolean arrays value, list and single value
ok    ok     return boolean value from sub
ok    ok     logical not, and, or, xor work correctly, also inplace
ok    ok     make sure that and,or,xor,not aren't getting replaced by the bitwise versions in the Ast
ok    ok     and, or, xor, not should work in expressions: print_ub((bb and true) as ubyte)
ok    ok     swap operands around on evaluating staticboolvar xor ba[0]
ok    ok     bitwise logical ops on bools give type error, including invert
ok    ok     arithmetic ops on bools give type error
ok    ok     logical ops on ints give type error
ok    ok     boolean values in ubyte array should give type error
ok    ok     type error for bool[3] derp = 99    and also for init value [1,0,1] and also for [true, false, 1, 0, 222]
ok    ok     while booleanvar==42  and   do..until booleanvar==42    should give type error
ok    ok     while not <integervar>   should give type error
ok    ok     while not <integer functioncall>   should give type error
ok    ok     while not cx16.mouse_pos()  should give condition type error
ok    ok     efficient code for manipulating bools in an array (normal and agumented assigns)
ok    ok     efficient code for if with only a goto in it
ok    ok     efficient code for if byte comparisons against 0 (== and !=)
ok    ok     efficient code for if word comparisons against 0 (== and !=)
ok    ok     efficient code for if float comparisons against 0 (== and !=)
ok    ok     efficient code for if byte comparisons against a value
ok    ok     efficient code for if word comparisons against a value
ok    ok     efficient code for if float comparisons against a value
ok    ok     efficient code for assignment byte comparisons against 0 (== and !=)
ok    ok     efficient code for assignment word comparisons against 0 (== and !=)
ok    ok     efficient code for assignment float comparisons against 0 (== and !=)
ok    ok     efficient code for assignment byte comparisons against a value
ok    ok     efficient code for assignment word comparisons against a value
ok    ok     efficient code for assignment float comparisons against a value
ok    ok     efficient code for if_cc conditional expressions
ok    ok     while boolean  should produce identical code as  while integer!=0  and code should be efficient
ok    ok     while not boolvar  -> can we get rid of the cmp? (6502 only?)
ok    FAIL   testmonogfx works
ok    .      check program sizes vs. master branch
===== ====== =======


retest all comparisons in if statements (byte, word, signed and unsigned) + all comparison assignments.  Against 0 and something else as 0.
with jump, indirect jump, no else block, and both if+else blocks.

check that the flood fill routine in gfx2 and paint still works.
re-allow typecast of const true/false back to ubytes 1 and 0?
re-allow typecast of const ubyte 0/1 to false/true boolean?


IR: add TEST instruction to test memory content and set N/Z flags, without affecting any register.
    replace all LOADM+CMPI #0  / LOAD #0+LOADM+CMP+BRANCH   by this instruction

