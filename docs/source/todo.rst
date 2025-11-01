TODO
====

- fix/check github issues.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- implement rest of long comparisons in IfElseAsmGen compareLongValues(): expressions operands that might clobber the R14-R15 registers...
- struct/ptr: implement the remaining TODOs in PointerAssignmentsGen.
- struct/ptr: optimize deref in PointerAssignmentsGen: optimize 'forceTemporary' to only use a temporary when the offset is >0
- struct/ptr: optimize the float copying in assignIndexedPointer() (also word and long?)
- struct/ptr: optimize augmented assignments to indexed pointer targets like sprptr[2]^^.y++  (these are now not performend in-place but as a regular assignment)
- struct/ptr: implement even more struct instance assignments (via memcopy) in CodeDesugarer (see the TODO) (add to documentation as well, paragraph 'Structs')
- struct/ptr: support const pointers (simple and struct types) (make sure to change codegen properly in all cases, change remark about this limitation in docs too)
- struct/ptr: support @nosplit pointer arrays?
- struct/ptr: support pointer to pointer?
- struct/ptr: support for typed function pointers?  (&routine could be typed by default as well then)
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid beween IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)
- make $8000000 a valid long integer (-2147483648) this is more involved than you think.  To make this work: long \|= $80000000
- make memory mapped variables support more constant expressions such as:  &uword  MyHigh = &mylong1+2
- fix the line, cols in Position, sometimes they count from 0 sometimes from 1, should both always be 1-based (is this the reason some source lines end up missing in the IR file?)
- handle Alias in a general way in LiteralsToAutoVarsAndRecombineIdentifiers instead of replacing it scattered over multiple functions
- After long variable type is completed: make all constants long by default (remove type name altogether), reduce to target type implictly if the actual value fits.
  This will break some existing programs that depend on value wrap arounds, but gives more intuitive constant number handling.
  Can give descriptive error message for old syntax that still includes the type name?
- improve ANTLR grammar with better error handling (as suggested by Qwen AI)
- add documentation for more library modules instead of just linking to the source code
- add an Index to the documentation
- allow memory() to occur in array initializer (maybe needed for 2 dimensional arrays?)
- Two- or even multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays?
- when a complete block is removed because unused, suppress all info messages about everything in the block being removed
- is "checkAssignmentCompatible" redundant (gets called just 1 time!) when we also have "checkValueTypeAndRange" ?
- enums?
- fix the c64 multiplexer example
- romable: should we have a way to explicitly set the memory address for the BSS area (add a -varsaddress and -slabsaddress options?)
- romable: fix remaining codegens (some for loops, see ForLoopsAsmGen)
- Kotlin: can we use inline value classes in certain spots? (domain types instead of primitives)
- add float support to the configurable compiler targets
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
- detect circular aliases and print proper error message for them
- Improve register load order in subroutine call args assignments:
  in certain situations (need examples!), the "wrong" order of evaluation of function call arguments is done which results
  in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
  Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().
- Does it make codegen easier if everything is an expression?  Start with the PtProgram ast classes, change statements to expressions that have (new) VOID data type. BUT probably not worth it if a new codegen is going to be based on the IR
- Can we support signed % (remainder) somehow?
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- more support for (64tass) SEGMENTS in the prog8 syntax itself? maybe %segment blah  in blocks?
- ability to use a sub instead of only a var for @bank ? what for though? dynamic bank/overlay loading?
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


IR/VM
-----
- getting it in shape for code generation...: the IR file should be able to encode every detail about a prog8 program (the VM doesn't have to actually be able to run all of it though!)
- fix call() return value handling (... what's wrong with it again?)
- proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- is it possible to use LOADFIELD/STOREFIELD instructions even more?
- make multiple classes of registers and maybe also categorize by life time , to prepare for better register allocation in the future
    SYSCALL_ARGS,        // Reserved for syscall arguments (r99000-99099, r99100-99199)
    FUNCTION_PARAMS,     // For passing function parameters
    FUNCTION_RETURNS,    // For function return values
    TEMPORARY,           // Short-lived temporary values
    LOCAL_VARIABLES,     // Local variables within functions
    GLOBAL_VARIABLES,    // Global/static variables
    HARDWARE_MAPPED,     // Mapped to CPU hardware registers
    LOOP_INDICES,        // Used as loop counters
    ADDRESS_CALCULATION  // Used for pointer arithmetic
  Categorizing registers by lifetime can significantly improve allocation:
   - Short-lived: Temporary registers used in expressions
   - Medium-lived: Local variables within a function
  Registers could be categorized by how frequently they're accessed:
   - Hot Registers: Frequently accessed (should be allocated to faster physical registers)
   - Warm Registers: Moderately accessed
   - Cold Registers: Rarely accessed (can be spilled to memory if needed)
  We already have type-based pools
    - byte, word, long, float registers

- pointer dt's are all reduced to just an uword (in the irTypeString method) - is this okay or could it be beneficial to reintroduce the actual pointer type information? See commit 88b074c208450c58aa32469745afa03e4c5f564a
- change the instruction format so an indirect register (a pointer) can be used more often, at least for the inplace assignment operators that operate on pointer
- register reuse to reduce the number of required variables in memory eventually. But can only re-use a register if a) it's the same type and b) if the second occurrence is not called from the first occurrence (otherwise the value gets overwritten!)
- reduce register usage via linear-scan algorithm (based on live intervals) https://anoopsarkar.github.io/compilers-class/assets/lectures/opt3-regalloc-linearscan.pdf
  don't forget to take into account the data type of the register when it's going to be reused!
- encode asmsub/extsub clobber info in the call , or maybe include these definitions in the p8ir file itself too.  (return registers are already encoded in the CALL instruction)
- implement fast code paths for TODO("inplace split....
- implement more TODOs in AssignmentGen
- do something with the 'split' tag on split word arrays
- add more optimizations in IRPeepholeOptimizer
- idea: replace all scalar variables that are not @shared by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters? Or not?  So the only variables that remain as variables are arrays and strings.
- the split word arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...? Should array operations be encoded in a more high level form in the IR?
- ExpressionCodeResult:  get rid of the separation between single result register and multiple result registers? maybe not, this requires hundreds of lines to change.. :(
- sometimes source lines end up missing in the output p8ir, for example the first assignment is gone in::

     sub start() {
     cx16.r0L = cx16.r1 as ubyte
     cx16.r0sL = cx16.r1s as byte }

More detailed example: not all source lines are correctly reported in the IR file,
for example the below subroutine only shows the sub() line::

    sub two() {
        cx16.r0 = peekw(ww + cx16.r0L * 2)
    }

and for example the below code omits line 5::

[examples/test.p8: line 4 col 6-8]  sub start() {
[examples/test.p8: line 6 col 10-13]  cx16.r2 = select2()
[examples/test.p8: line 7 col 10-13]  cx16.r3 = select3()
[examples/test.p8: line 8 col 10-13]  cx16.r4 = select4()
[examples/test.p8: line 9 col 10-13]  cx16.r5 = select5()


.. code-block::

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


Libraries
---------
- Add split-word array sorting routines to sorting module?
- pet32 target: make syslib more complete (missing kernal routines)?
- need help with: PET disk routines (OPEN, SETLFS etc are not exposed as kernal calls)
- c128 target: make syslib more complete (missing kernal routines)?


Optimizations
-------------

- optimize inplaceLongShiftRight() for byte aligned cases
- more optimized operator handling of different types, for example uword a ^ byte b now does a type cast of b to word first
- optimize longEqualsValue() for const and variable operands to not assign needlessly to R0-R3.
- optimize optimizedBitwiseExpr()  for const and variable operands to not assign needlessly to R0-R3.
- optimize inplacemodificationLongWithLiteralval() for more shift values such as 8, 16, 24 etc but take sign bit into account!
- optimize simple cases in funcPeekL and funcPokeL
- bind types in the Ast much sooner than the simplifiedAst creation, so that we maybe could get rid of InferredType ?
- longvar = lptr^^  now goes via temporary registers, optimize this to avoid using temps. Also check lptr^^ = lvar.
- Port benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up.
- Since fixing the missing zp-var initialization, programs grew in size again because STZ's reappeared. Can we add more intelligent (and correct!) optimizations to remove those STZs that might be redundant again?
- in Identifier: use typedarray of strings instead of listOf? Other places?
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- Optimize the IfExpression code generation to be more like regular if-else code.  (both 6502 and IR) search for "TODO don't store condition as expression"
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
  This will probably need the register categorization from the IR explained there, for the old 6502 codegen there is not enough information to act on
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code, those checks should probably all be removed, or be made permanent
