TODO
====

For next release
^^^^^^^^^^^^^^^^
- fix assembler application included file loading problem (hello4.asm)
- x16: add new keyboard APIs https://github.com/commanderx16/x16-docs/blob/master/Commander%20X16%20Programmer%27s%20Reference%20Guide.md#keyboard
- x16: optimize diskio load_raw because headerless files are now supported https://github.com/commanderx16/x16-rom/pull/216
  note: must still work on c64/c128 that don't have this!
- x16: fix wormfood (requires new VTUI lib)

- vm codegen: When
- vm codegen: Pipe expression
- vm codegen: validate that PtFunctionCall translation works okay with resultregister, and multiple paramsters in correct order
- vm: support no globals re-init option
- vm: how to remove all unused subroutines? (for asm, 64tass used to do this)
- vm: rather than being able to jump to any 'address' (IPTR), use 'blocks'
- vm codegen/assembler: variable memory locations should also be referenced by the variable name instead of just the address
- when the vm is stable and *if* its language can get promoted to prog8 IL, the variable allocation should be changed.
  It's now done before the vm code generation, but the IL should probably not depend on the allocations already performed.
  So the CodeGen doesn't do VariableAlloc *before* the codegen, but as a last step.

...


Need help with
^^^^^^^^^^^^^^
- c128 target: various machine specific things (free zp locations, how banking works, getting the floating point routines working, ...)
- atari target: more details details about the machine, fixing library routines. I have no clue whatsoever.
- see the :ref:`portingguide` for details on what information is needed.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:

- vm code gen: don't reuse registers and don't pre allocate variables? (except strings + arrays) but instead put them into registers too
    then we ALMOST have Static Single Assignment form in the VM code.  But what can we use that for?
- pipe operator: allow non-unary function calls in the pipe that specify the other argument(s) in the calls.
- writeAssembly(): make it possible to actually get rid of the VarDecl nodes by fixing the rest of the code mentioned there.
- make everything an expression? (get rid of Statements. Statements are expressions with void return types?).
- allow "xxx" * constexpr  (where constexpr is not a number literal), now gives expression error not same type
- make it possible to inline non-asmsub routines that just contain a single statement (return, functioncall, assignment)
  but this requires all identifiers in the inlined expression to be changed to fully scoped names.
  If we can do that why not perhaps also able to inline multi-line subroutines? Why would it be limited to just 1 line? Maybe to protect against code bloat.
  Inlined subroutines cannot contain further nested subroutines!
- simplifyConditionalExpression() should not split expression if it still results in stack-based evaluation, but how does it know?
- simplifyConditionalExpression() sometimes introduces needless assignment to r9 tempvar (scenario sought)
- consider adding McCarthy evaluation to shortcircuit and and or expressions. First do ifs by splitting them up? Then do expressions that compute a value?
- use more of Result<> to handle errors/ nulls better?
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``p8v_``? Or not worth it (most 3 letter opcodes as variables are nonsensical anyway)
  then we can get rid of the instruction lists in the machinedefinitions as well?
- [problematic due to 64tass:] add a compiler option to not remove unused subroutines. this allows for building library programs. But this won't work with 64tass's .proc ...
  Perhaps replace all uses of .proc/.pend by .block/.bend will fix that?
  (but we lose the optimizing aspect of the assembler where it strips out unused code.
  There's not really a dynamic switch possible as all assembly lib code is static and uses one or the other)
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for this)
- add special (u)word array type (or modifier?) that puts the array into memory as 2 separate byte-arrays 1 for LSB 1 for MSB -> allows for word arrays of length 256

Libraries:

- fix the problems in c128 target, and flesh out its libraries.
- fix the problems in atari target, and flesh out its libraries.
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- optimize several inner loops in gfx2 even further?
- add modes 2 and 3 to gfx2 (lowres 4 color and 16 color)?
- add a flood fill routine to gfx2?
- add a diskio.f_seek() routine for the Cx16 that uses its seek dos api? (only if that's stable)

Expressions:

- rethink the whole "isAugmentable" business.  Because the way this is determined, should always also be exactly mirrorred in the AugmentableAssignmentAsmGen or you'll get a crash at code gen time.
- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code that uses a fixed number of predetermined value 'variables'?
  "Three address code" was mentioned.  https://en.wikipedia.org/wiki/Three-address_code
  these variables have to be unique for each subroutine because they could otherwise be interfered with from irq routines etc.
- this removes the need for the BinExprSplitter? (which is problematic and very limited now)
  and perhaps as well the assignment splitting in  BeforeAsmAstChanger too

Optimizations:

- various optimizers should/do skip stuff if compTarget.name==VMTarget.NAME.  Once (if?) 6502-codegen is no longer done from
  the old CompilerAst, those checks should probably be removed.
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served
- translateUnaryFunctioncall() in BuiltinFunctionsAsmGen: should be able to assign parameters to a builtin function directly from register(s), this will make the use of a builtin function in a pipe expression more efficient without using a temporary variable
   compare ``aa = startvalue(1) |> sin8u() |> cos8u() |> sin8u() |> cos8u()``
   versus: ``aa = cos8u(sin8u(cos8u(sin8u(startvalue(1)))))``  the second one contains no sta cx16.r9L in between.
- AssignmentAsmGen.assignExpression() -> better code gen for assigning boolean comparison expressions
- when a for loop's loopvariable isn't referenced in the body, and the iterations are known, replace the loop by a repeatloop
  but we have no efficient way right now to see if the body references a variable.
- AssignmentAsmGen: "real optimized code for comparison expressions that yield a boolean result value"
- automatically convert if statements that test for multiple values (if X==1 or X==2..) to if X in [1,2,..] statements, instead of just a warning.
- introduce byte-index operator to avoid index multiplications in loops over arrays? see github issue #4
