=================
Technical details
=================

All variables are static in memory
----------------------------------

All variables are allocated statically, there is no concept of dynamic heap or stack frames.
Essentially all variables are global (but scoped) and can be accessed and modified anywhere,
but care should be taken of course to avoid unexpected side effects.

Especially when you're dealing with interrupts or re-entrant routines: don't modify variables
that you not own or else you will break stuff.


Software stack for expression evaluation
----------------------------------------

Prog8 uses a software stack to evaluate complex expressions that it can't calculate in-place or
directly into the target variable, register, or memory location.

'software stack' means: seperated and not using the processor's hardware stack.

The software stack is implemented as follows:

- 2 pages of memory are allocated for this, exact locations vary per machine target.
  For the C-64 they are set at $ce00 and $cf00 (so $ce00-$cfff is reserved).
  For the Commander X16 they are set at $0400 and $0500 (so $0400-$05ff are reserved).
  This default location can be overridden using the `-esa` command line option.
- these are the high and low bytes of the values on the stack (it's a 'split 16 bit word stack')
- for byte values just the lsb page is used, for word values both pages
- float values (5 bytes) are chopped up into 2 words and 1 byte on this stack.
- the X register is permanently allocated to be the stack pointer in the software stack.
- you can use the X register as long as you're not using the software stack.
  But you *must* make sure it is saved and restored after the code that modifies it,
  otherwise the evaluation stack gets corrupted.

Subroutine Calling Convention
-----------------------------

Calling a subroutine requires three steps:

#. preparing the arguments (if any) and passing them to the routine
#. calling the routine
#. preparing the return value (if any) and returning that from the call.


Calling the routine is just a simple JSR instruction, but the other two work like this:


``asmsub`` routines
^^^^^^^^^^^^^^^^^^^

These are usually declarations of kernal (ROM) routines or low-level assembly only routines,
that have their arguments solely passed into specific registers.
Sometimes even via a processor status flag such as the Carry flag.
Return values also via designated registers.
The processor status flag is preserved on returning so you can immediately act on that for instance
via a special branch instruction such as ``if_z`` or ``if_cs`` etc.


regular subroutines
^^^^^^^^^^^^^^^^^^^

- subroutine parameters are just variables scoped to the subroutine.
- the arguments passed in a call are evaluated (using the eval-stack if needed) and then
  copied into those variables.
  Using variables for this sometimes can seem inefficient but it's required to allow subroutines to work locally
  with their parameters and allow them to modify them as required, without changing the
  variables used in the call's arguments.  If you want to get rid of this overhead you'll
  have to make an ``asmsub`` routine in assembly instead.
- the order of evaluation of subroutine call arguments *is unspecified* and should not be relied upon.
- the return value is passed back to the caller via cpu register(s):
  Byte values will be put in ``A`` .
  Word values will be put in ``A`` + ``Y`` register pair.
  Float values will be put in the ``FAC1`` float 'register' (Basic allocated this somewhere in ram).


Calls to builtin functions are treated in a special way:
Generally if they have a single argument it's passed in a register or register pair.
Multiple arguments are passed like a normal subroutine, into variables.
Some builtin functions have a fully custom implementation.


The compiler will warn about routines that are called and that return a value, if you're not
doing something with that returnvalue. This can be on purpose if you're simply not interested in it.
Use the ``void`` keyword in front of the subroutine call to get rid of the warning in that case.


The 6502 CPU's X-register: off-limits
-------------------------------------

Prog8 uses the cpu's X-register as a pointer in its internal expression evaluation stack.
When only writing code in Prog8, this is taken care of behind the scenes for you by the compiler.
However when you are including or linking with assembly routines or kernal/ROM calls that *do*
use the X register (either clobbering it internally, or using it as a parameter, or return value register),
those calls will destroy Prog8's stack pointer and this will result in invalid calculations.

You should avoid using the X register in your assembly code, or take preparations.
If you make sure that the value of the X register is preserved before calling a routine
that uses it, and restored when the routine is done, you'll be ok.

Routines that return a value in the X register can be called from Prog8 but the return value is
inaccessible unless you write a short piece of inline assembly code to deal with it yourself, such as::

    ubyte returnvalue

    %asm {{
        stx  P8ZP_SCRATCH_REG       ; use 'phx/plx' if using 65c02 cpu
        ldx  #10
        jsr  routine_using_x
        stx  returnvalue
        ldx  P8ZP_SCRATCH_REG
    }}
    ; now use 'returnvalue' variable

Prog8 also provides some help to deal with this:

- you should use a ``clobbers(X)`` specification for asmsub routines that modify the X register; the compiler will preserve it for you automatically when such a routine is called
- the ``rsavex()`` and ``rrestorex()`` builtin functions can preserve and restore the X register
- the ``rsave()`` and ``rrestore()`` builtin functions can preserve and restore *all* registers (but this is very slow and overkill if you only need to save X)


Compiler Internals
------------------

Here is a diagram of how the compiler translates your program source code into a binary program:

.. image:: prog8compiler.svg

Some notes and references into the compiler's source code modules:

#. The ``compileProgram()`` function (in the ``compiler`` module) does all the coordination and basically drives all of the flow shown in the diagram.
#. ANTLR is a Java parser generator and is used for initial parsing of the source code. (``parser`` module)
#. Most of the compiler and the optimizer operate on the *Compiler AST*. These are complicated
   syntax nodes closely representing the Prog8 program structure. (``compilerAst`` module)
#. For code generation, a much simpler *intermediate AST* has been defined that replaces the *Compiler AST*.
   Most notably, node type information is now baked in. (``codeCore`` module)
#. An *Intermediate Representation* has been defined that is generated from the intermediate AST. This IR
   is more or less a machine code language for a virtual machine - and indeed this is what the built-in
   prog8 VM will execute if you use the 'virtual' compilation target and use ``-emu`` to launch the VM.
   (``intermediate`` and ``codeGenIntermediate`` modules, and ``virtualmachine`` module for the VM related stuff)
#. Currently the 6502 ASM code generator still works directly on the *Compiler AST*. A future version
   should replace this by working on the IR code, and should be much smaller and simpler.
   (``codeGenCpu6502`` module)
#. Other code generators may either work on the intermediate AST or on the IR. Selection of what code generator
   to use is mostly based on the compilation target, and is done in the ``asmGeneratorFor()`` function.
