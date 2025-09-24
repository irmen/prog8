TODO
====


STRUCTS and TYPED POINTERS
--------------------------

- make this array indexed assignment work:   ^^Node np  /  np[2]^^.field = 9999  (same for pointer arrays!)    likely needs more support in the assignment target class  (remove Note in docs when fixed)
- implement the remaining TODO's in PointerAssignmentsGen.
- optimize deref in PointerAssignmentsGen: optimize 'forceTemporary' to only use a temporary when the offset is >0
- optimize the float copying in assignIndexedPointer() (also word?)
- implement even more struct instance assignments (via memcopy) in CodeDesugarer (see the TODO) (add to documentation as well, paragraph 'Structs')
- support @nosplit pointer arrays?
- support pointer to pointer?
- support for typed function pointers?  (&routine could be typed by default as well then)
- really fixing the pointer dereferencing issues (cursed hybrid beween IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^

- improve ANTLR grammar with better error handling (as suggested by Qwen AI)
- allow memory() to occur in array initializer
- when a complete block is removed because unused, suppress all info messages about everything in the block being removed
- fix the line, cols in Position, sometimes they count from 0 sometimes from 1
- is "checkAssignmentCompatible" redundant (gets called just 1 time!) when we also have "checkValueTypeAndRange" ?
- enums?
- romable: should we have a way to explicitly set the memory address for the BSS area (add a -varsaddress and -slabsaddress options?)
- romable: fix remaining codegens (some for loops, see ForLoopsAsmGen)
- Kotlin: can we use inline value classes in certain spots? (domain types instead of primitives)
- add float support to the configurable compiler targets
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
- Improve register load order in subroutine call args assignments:
  in certain situations (need examples!), the "wrong" order of evaluation of function call arguments is done which results
  in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
  Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().
- Does it make codegen easier if everything is an expression?  Start with the PtProgram ast classes, change statements to expressions that have (new) VOID data type
- Can we support signed % (remainder) somehow?
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays. Probaby only useful once we have typed pointers. (addressed in 'struct' branch)
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- [much work:] more support for (64tass) SEGMENTS in the prog8 syntax itself?
- ability to use a sub instead of only a var for @bank ? what for though? dynamic bank/overlay loading?
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


IR/VM
-----
- make MSIG instruction set flags and skip cmp #0 afterwards   (if msb(x)>0)
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
    - byte, word, float registers

- pointer dt's are all reduced to just an uword (in the irTypeString method) - is this okay or could it be beneficial to reintroduce the actual pointer type information? See commit 88b074c208450c58aa32469745afa03e4c5f564a
- change the instruction format so an indirect register (a pointer) can be used more often, at least for the inplace assignment operators that operate on pointer
- getting it in shape for code generation...: the IR file should be able to encode every detail about a prog8 program (the VM doesn't have to actually be able to run all of it though!)
- fix call() return value handling (... what's wrong with it again?)
- encode asmsub/extsub clobber info in the call , or maybe include these definitions in the p8ir file itself too.  (return registers are already encoded in the CALL instruction)
- proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- implement fast code paths for TODO("inplace split....
- implement more TODOs in AssignmentGen
- do something with the 'split' tag on split word arrays
- add more optimizations in IRPeepholeOptimizer
- apparently for SSA form, the IRCodeChunk is not a proper "basic block" yet because the last operation should be a branch or return, and no other branches
- reduce register usage via linear-scan algorithm (based on live intervals) https://anoopsarkar.github.io/compilers-class/assets/lectures/opt3-regalloc-linearscan.pdf
  don't forget to take into account the data type of the register when it's going to be reused!
- idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- ExpressionCodeResult:  get rid of the separation between single result register and multiple result registers? maybe not, this requires hundreds of lines to change
- sometimes source lines end up missing in the output p8ir, for example the first assignment is gone in:
     sub start() {
     cx16.r0L = cx16.r1 as ubyte
     cx16.r0sL = cx16.r1s as byte }
     more detailed example:

not all source lines are correctly reported in the IR file,
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

- more optimized operator handling of different types, for example uword a ^ byte b now does a type cast of b to word first
- Port benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up.
- Since fixing the missing zp-var initialization, programs grew in size again because STZ's reappeared. Can we add more intelligent (and correct!) optimizations to remove those STZs that might be redundant again?
- in Identifier: use typedarray of strings instead of listOf? Other places?
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- Optimize the IfExpression code generation to be more like regular if-else code.  (both 6502 and IR) search for "TODO don't store condition as expression"
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
  This will probably need the register categorization from the IR explained there, for the old 6502 codegen there is not enough information to act on
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code, those checks should probably be removed, or be made permanent
