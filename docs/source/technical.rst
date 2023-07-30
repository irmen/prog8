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

Variables that are not put into zeropage, will be put into a special 'BSS' section for the assembler.
This section is usually placed at the end of the resulting program but because it only contains empty space
it won't actually increase the size of the resulting program binary.
Prog8 takes care of properly filling this memory area with zeros at program startup and then reinitializes
the subset of variables that have a nonzero initialization value.

It is possible to relocate the BSS section using a compiler option
so that more system ram is available for the program code itself.


.. _symbol-prefixing:

Symbol prefixing in generated Assembly code
-------------------------------------------

*All* symbols in the prog8 program will be prefixed with ``p8_`` in the generated assembly code.
This is to avoid naming conflicts with CPU registers, assembly instructions, etc.
So if you're referencing symbols from the prog8 program in inlined assembly code, you have to take
this into account. Stick a ``p8_`` in front of everything that you want to reference that is coming
from a prog8 source file.
All elements in scoped names such as ``main.routine.var1`` are prefixed so this becomes ``p8_main.p8_routine.p8_var1``.

.. attention::
    Symbols from library modules are *not* prefixed and can be used
    in assembly code as-is. So you can write::

        %asm {{
            lda  #'a'
            jsr  cbm.CHROUT
        }}


Subroutine Calling Convention
-----------------------------

Calling a subroutine requires three steps:

#. preparing the arguments (if any) and passing them to the routine
#. calling the routine
#. preparing the return value (if any) and returning that from the call.


Calling the routine is just a simple JSR instruction, but the other two work like this:


``asmsub`` routines
^^^^^^^^^^^^^^^^^^^

These are usually declarations of Kernal (ROM) routines or low-level assembly only routines,
that have their arguments solely passed into specific registers.
Sometimes even via a processor status flag such as the Carry flag.
Return values also via designated registers.
The processor status flag is preserved on returning so you can immediately act on that for instance
via a special branch instruction such as ``if_z`` or ``if_cs`` etc.


regular subroutines
^^^^^^^^^^^^^^^^^^^

- subroutine parameters are just variables scoped to the subroutine.
- the arguments passed in a call are evaluated and then copied into those variables.
  Using variables for this sometimes can seem inefficient but it's required to allow subroutines to work locally
  with their parameters and allow them to modify them as required, without changing the
  variables used in the call's arguments.  If you want to get rid of this overhead you'll
  have to make an ``asmsub`` routine in assembly instead.
- the order of evaluation of subroutine call arguments *is unspecified* and should not be relied upon.
- the return value is passed back to the caller via cpu register(s):
  Byte values will be put in ``A`` .
  Word values will be put in ``A`` + ``Y`` register pair.
  Float values will be put in the ``FAC1`` float 'register' (BASIC allocated this somewhere in ram).


Calls to builtin functions are treated in a special way:
Generally if they have a single argument it's passed in a register or register pair.
Multiple arguments are passed like a normal subroutine, into variables.
Some builtin functions have a fully custom implementation.


The compiler will warn about routines that are called and that return a value, if you're not
doing something with that returnvalue. This can be on purpose if you're simply not interested in it.
Use the ``void`` keyword in front of the subroutine call to get rid of the warning in that case.


Compiler Internals
------------------

Here is a diagram of how the compiler translates your program source code into a binary program:

.. image:: prog8compiler.svg

Some notes and references into the compiler's source code modules:

#. The ``compileProgram()`` function (in the ``compiler`` module) does all the coordination and basically drives all of the flow shown in the diagram.
#. ANTLR is a Java parser generator and is used for initial parsing of the source code. (``parser`` module)
#. Most of the compiler and the optimizer operate on the *Compiler AST*. These are complicated
   syntax nodes closely representing the Prog8 program structure. (``compilerAst`` module)
#. For code generation, a much simpler AST has been defined that replaces the *Compiler AST*.
   Most notably, node type information is now baked in. (``codeCore`` module, Pt- classes)
#. An *Intermediate Representation* has been defined that is generated from the intermediate AST. This IR
   is more or less a machine code language for a virtual machine - and indeed this is what the built-in
   prog8 VM will execute if you use the 'virtual' compilation target and use ``-emu`` to launch the VM.
   (``intermediate`` and ``codeGenIntermediate`` modules, and ``virtualmachine`` module for the VM related stuff)
#. The code generator backends all implement a common interface ``ICodeGeneratorBackend`` defined in the ``codeCore`` module.
   Currently they get handed the program Ast, Symboltable and several other things.
   If the code generator wants it can use the ``IRCodeGen`` class from the ``codeGenIntermediate`` module
   to convert the Ast into IR first. The VM target uses this, but the 6502 codegen doesn't right now.
