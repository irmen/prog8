===============
Technical stuff
===============

All variables are static in memory
----------------------------------

All variables are allocated statically, there is no concept of dynamic heap or stack frames.
Essentially all variables are global (but scoped) and can be accessed and modified anywhere,
but care should be taken ofcourse to avoid unexpected side effects.

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
#. preparig the return value (if any) and returning that from the call.


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
  This sometimes can seem inefficient but it's required to allow subroutines to work locally
  with their parameters and allow them to modify them as required, without changing the
  variables used in the call's arguments.  If you want to get rid of this overhead you'll
  have to make an ``asmsub`` routine in assembly instead.
- the return value is passed back to the caller via cpu register(s):
  Byte values will be put in ``A`` .
  Word values will be put in ``A`` + ``Y`` register pair.
  Float values will be put in the ``FAC1`` float 'register' (Basic allocated this somewhere in ram).


Calls to builtin functions are treated in a special way:
Generally if they have a single argument it's passed in a register or register pair.
Multiple arguments are passed like a normal subroutine, into variables.
Some builtin functions have a fully custom implementation.


The compiler will warn about routines that are called and that return a value, if you're not
doing something with that returnvalue. This can be on purpuse if you're simply not interested in it.
Use the ``void`` keyword in front of the subroutine call to get rid of the warning in that case.
