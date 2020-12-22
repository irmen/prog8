====
TODO
====

- optimize (byte) bitshifting 1<<x (and 2**x) via lookup table 1,2,4,8,...
- Cx16 target: support full-screen 640x480 and 320x240 graphics? That requires our own custom graphics routines though to draw lines, and plot pixels.
- hoist all variable declarations up to the subroutine scope *before* even the constant folding takes place (to avoid undefined symbol errors when referring to a variable from another nested scope in the subroutine)
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as '_'
- option to load the built-in library files from a directory instead of the embedded ones (for easier library development/debugging)
- use VIC banking to move up the graphics bitmap memory location. Move it to $e000 under the kernal rom?
- some support for recursive subroutines?
    - via %option recursive?: allocate all params and local vars on estack, don't allow nested subroutines, can begin by first not allowing any local variables just fixing the parameters
    - Or via a special recursive call operation that copies the current values of all local vars (including arguments) to the stack, replaces the arguments, jsr subroutine, and after returning copy the stack back to the local variables
- get rid of all other TODO's in the code ;-)

More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- further optimize assignment codegeneration, such as the following:
- binexpr splitting (beware self-referencing expressions and asm code ballooning though)
- more optimizations on the language AST level
- more optimizations on the final assembly source level
- note: subroutine inlining is abandoned because of problems referencing non-local stuff. Can't move everything around.


Eval stack redesign? (lot of work)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The eval stack is now a split lsb/msb stack using X as the stackpointer.
Is it easier/faster to just use a single page unsplit stack?
It could then even be moved into the zeropage to reduce code size and slowness.

Or just move the LSB portion into a slab of the zeropage.

Allocate a fixed word in ZP that is the Top Of Stack value so we can always operate on TOS directly
without having to index with X into the eval stack all the time?
This could GREATLY improve code size and speed for operations that work on just a single value.


Bug Fixing
^^^^^^^^^^
Ofcourse there are always bugs to fix ;)


Misc
^^^^

Several ideas were discussed on my reddit post
https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/
