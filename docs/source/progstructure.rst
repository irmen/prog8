.. _programstructure:

=================
Program Structure
=================

This chapter describes a high level overview of the elements that make up a program.
Details about each of them, and the syntax, are discussed in the :ref:`syntaxreference` chapter.


Elements of a program
---------------------

.. data:: Program

	Consists of one or more *modules*.

.. data:: Module

	A file on disk with the ``.ill`` suffix. It contains *directives* and *code blocks*.
	Whitespace and indentation in the source code are arbitrary and can be tabs or spaces or both.
	You can also add *comments* to the source code.
	One moudule file can *import* others, and also import *library modules*.

.. data:: Comments

	Everything after a semicolon ``;`` is a comment and is ignored by the compiler.
	If the whole line is just a comment, it will be copied into the resulting assembly source code.
	This makes it easier to understand and relate the generated code. Examples::

		A = 42    ; set the initial value to 42
		; next is the code that...

.. data:: Directive

	These are special instructions for the compiler, to change how it processes the code
	and what kind of program it creates. A directive is on its own line in the file, and
	starts with ``%``, optionally followed by some arguments.

.. data:: Code block

	A block of actual program code. It defines a *scope* (also known as 'namespace') and
	can contain IL65 *code*, *variable declarations* and *subroutines*.
	More details about this below: :ref:`blocks`.

.. data:: Variable declarations

	The data that the code works on is stored in variables ('named values that can change').
	The compiler allocates the required memory for them.
	There is *no dynamic memory allocation*. The storage size of all variables
	is fixed and is determined at compile time.
	Variable declarations tend to appear at the top of the code block that uses them.
	They define the name and type of the variable, and its initial value.
	IL65 supports a small list of data types, including special 'memory mapped' types
	that don't allocate storage but instead point to a fixed location in the address space.

.. data:: Code

	These are the instructions that make up the program's logic. There are different kinds of instructions
	('statements' is a better name):

	- value assignment
	- looping  (for, while, repeat, unconditional jumps)
	- conditional execution (if - then - else, and conditional jumps)
	- subroutine calls
	- label definition

.. data:: Subroutine

	Defines a piece of code that can be called by its name from different locations in your code.
	It accepts parameters and can return result values.
	It can define its own variables but it's not possible to define subroutines nested in other subroutines.
	To keep things simple, you can only define subroutines inside code blocks from a module.

.. data:: Label

	To label a position in your code where you can jump to from another place, you use a label like this::

		nice_place:
				; code ...

	It's an identifier followed by a colon ``:``. It's allowed to put the next statement on
	the same line, after the label.
	You can jump to it with a jump statement elsewhere. It is also possible to use a
	subroutine call to a label (but without parameters and return value).


.. data:: Scope

	Also known as 'namespace', this is a named box around the symbols defined in it.
	This prevents name collisions (or 'namespace pollution'), because the name of the scope
	is needed as prefix to be able to access the symbols in it.
	Anything *inside* the scope can refer to symbols in the same scope without using a prefix.
	There are three scopes in IL65:

	- global (no prefix)
	- code block
	- subroutine

	Modules are *not* a scope! Everything defined in a module is merged into the global scope.


.. _blocks:

Blocks, Scopes, and accessing Symbols
-------------------------------------

Blocks are the separate pieces of code and data of your program. They are combined
into a single output program.  No code or data can occur outside a block. Here's an example::

	~ main $c000 {
		; this is code inside the block...
	}


The name of a block must be unique in your entire program.
Also be careful when importing other modules; blocks in your own code cannot have
the same name as a block defined in an imported module or library.

It's possible to omit this name, but then you can only refer to the contents of the block via its absolute address,
which is required in this case. If you omit *both* name and address, the block is *ignored* by the compiler (and a warning is displayed).
This is a way to quickly "comment out" a piece of code that is unfinshed or may contain errors that you
want to work on later, because the contents of the ignored block are not fully parsed either.

The address can be used to place a block at a specific location in memory.
Usually it is omitted, and the compiler will automatically choose the location (usually immediately after
the previous block in memory).
The address must be >= ``$0200`` (because ``$00``--``$ff`` is the ZP and ``$100``--``$200`` is the cpu stack).

A block is also a *scope* in your program so the symbols in the block don't clash with
symbols of the same name defined elsewhere in the same file or in another file.
You can refer to the symbols in a particular block by using a *dotted name*: ``blockname.symbolname``.
Labels inside a subroutine are appended again to that; ``blockname.subroutinename.label``.

Every symbol is 'public' and can be accessed from elsewhere given its dotted name.


**The special "ZP" ZeroPage block**

Blocks named "ZP" are treated a bit differently: they refer to the ZeroPage.
The contents of every block with that name (this one may occur multiple times) are merged into one.
Its start address is always set to ``$04``, because ``$00 - $01`` are used by the hardware
and ``$02 - $03`` are reserved as general purpose scratch registers.


Program Start and Entry Point
-----------------------------

Your program must have a single entry point where code execution begins.
The compiler expects a ``start`` subroutine in the ``main`` block for this,
taking no parameters and having no return value.
As any subroutine, it has to end with a ``return`` statement (or a ``goto`` call)::

    ~ main {
        sub start () -> ()  {
            ; program entrypoint code here
            return
        }
    }

The ``main`` module is always relocated to the start of your programs
address space, and the ``start`` subroutine (the entrypoint) will be on the
first address. This will also be the address that the BASIC loader program (if generated)
calls with the SYS statement.
