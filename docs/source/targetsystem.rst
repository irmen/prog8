***************************
Target system specification
***************************

Prog8 targets the following hardware:

- 8 bit MOS 6502/65c02/6510 CPU
- 64 Kb addressable memory (RAM or ROM)
- optional use of memory mapped I/O registers
- optional use of system ROM routines

Currently these machines can be selected as a compilation target (via the ``-target`` compiler argument):

- 'c64': the Commodore 64
- 'cx16': the `Commander X16 <https://www.commanderx16.com/>`_
- 'c128': the Commodore 128  (*limited support*)
- 'atari': the Atari 800 XL  (*experimental support*)
- 'virtual': a builtin virtual machine

This chapter explains some relevant system details of the c64 and cx16 machines.

.. hint::
    If you only use standard Kernal and prog8 library routines,
    it is often possible to compile the *exact same program* for
    different machines (just change the compilation target flag)!


Memory Model
============

Generic 6502 Physical address space layout
------------------------------------------

The 6502 CPU can address 64 kilobyte of memory.
Most of the 64 kilobyte address space can be used by Prog8 programs.
This is a hard limit: there is no support for RAM expansions or bank switching built natively into the language.

======================  ==================  ========
memory area             type                note
======================  ==================  ========
``$00``--``$ff``        zeropage            contains many sensitive system variables
``$100``--``$1ff``      Hardware stack      used by the CPU, normally not accessed directly
``$0200``--``$ffff``    Free RAM or ROM     free to use memory area, often a mix of RAM and ROM
                                            depending on the specific computer system
======================  ==================  ========


Memory map for the C64 and the X16
----------------------------------

This is the default memory map of the 64 Kb addressable memory for those two systems.
Both systems have ways to alter the memory map and/or to switch memory banks, but that is not shown here.

.. image:: memorymap.svg

Footnotes for the Commander X16
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
*Golden Ram $0400 - $07FF*
    *reserved:* $0700 - $07FF (expression evaluation stack)

    *free to use:* $0400 - $06FF

*Zero Page $0000 - $00FF*
    $00 and $01 are hardwired as Rom and Ram banking registers.

    $02 - $21 are the 16 virtual cx16 registers R0-R15.

    $22 - $7F are free to use, and Prog8 utilizes this to put variables in automatically.

    The top half of the ZP ($80-$FF) is reserved for use by the Kernal and Basic in normal operation.
    Zero page use by Prog8 can be manipulated with the ``%zeropage`` directive, various options
    may free up more locations for use by Prog8.


Footnotes for the Commodore 64
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

*RAM $C000-$CFFF*
    *reserved:* $CF00 - $CFFF (expression evaluation stack)
    *this includes:* $CF00 - $CF20 for the 16 virtual cx16 registers R0-R15

    *free to use:* $C000 - $CEFF

*Zero Page $0000 - $00FF*
    Consider the full zero page to be reserved for use by the Kernal and Basic in normal operation.
    Zero page use by Prog8 can be manipulated with the ``%zeropage`` directive, various options
    may free up more locations for use by Prog8.


Zero page usage by the Prog8 compiler
-------------------------------------
Prog8 knows what addresses are safe to use in the various ZP handling configurations.
It will use the free ZP addresses to place its ZP variables in,
until they're all used up. If instructed to output a program that takes over the entire
machine, (almost) all of the ZP addresses are suddenly available and will be used.

**zeropage handling is configurable:**
There's a global program directive to specify the way the compiler
treats the ZP for the program. The default is to be reasonably restrictive to use the
part of the ZP that is not used by the C64's Kernal routines.
It's possible to claim the whole ZP as well (by disabling the operating system or Kernal).
If you want, it's also possible to be more restrictive and stay clear of the addresses used by BASIC routines too.
This allows the program to exit cleanly back to a BASIC ready prompt - something that is not possible in the other modes.


IRQs and the zeropage
^^^^^^^^^^^^^^^^^^^^^

The normal IRQ routine in the C64's Kernal will read and write several addresses in the ZP
(such as the system's software jiffy clock which sits in ``$a0 - $a2``):

``$a0 - $a2``; ``$91``; ``$c0``; ``$c5``; ``$cb``; ``$f5 - $f6``

These addresses will *never* be used by the compiler for ZP variables, so variables will
not interfere with the IRQ routine and vice versa. This is true for the normal ZP mode but also
for the mode where the whole system and ZP have been taken over.
So the normal IRQ vector can still run and will be when the program is started!




CPU
===

Directly Usable Registers
-------------------------

The hardware CPU registers are not directly accessible from regular Prog8 code.
If you need to mess with them, you'll have to use inline assembly.
Be extra wary of the ``X`` register because it is used as an evaluation stack pointer and
changing its value you will destroy the evaluation stack and likely crash the program.

The status register (P) carry flag and interrupt disable flag can be written via a couple of special
builtin functions (``set_carry()``, ``clear_carry()``, ``set_irqd()``,  ``clear_irqd()``),
and read via the ``read_flags()`` function.

The 16 'virtual' 16-bit registers that are defined on the Commander X16 machine are not real hardware
registers and are just 16 memory-mapped word values that you *can* access directly.


IRQ Handling
============

Normally, the system's default IRQ handling is not interfered with.
You can however install your own IRQ handler (for clean separation, it is advised to define it inside its own block).
There are a few library routines available to make setting up C64 60hz IRQs and Raster IRQs a lot easier (no assembly code required).

For the C64 these routines are::

    c64.set_irq(uword handler_address, boolean useKernal)
    c64.set_rasterirq(uword handler_address, uword rasterline, boolean useKernal)
    c64.restore_irq()     ; set everything back to the systems default irq handler

And for the Commander X16::

    cx16.set_irq(uword handler_address, boolean useKernal)          ; vsync irq
    cx16.set_rasterirq(uword handler_address, uword rasterline)     ; note: disables Kernal irq handler! sys.wait() won't work anymore
    cx16.restore_irq()     ; set everything back to the systems default irq handler


The Commander X16 syslib does provides two additional routines that should be used *in your IRQ handler routine* if it uses the Vera registers.
They take care of saving and restoring the Vera state of the interrupted main program, otherwise the IRQ handler's manipulation
will corrupt any Vera operations that were going on in the main program. The routines are::

    cx16.push_vera_context()
    ; ... do your work that uses vera here...
    cx16.pop_vera_context()

.. caution::
    The Commander X16's 16 'virtual registers' R0-R15 are located in zeropage and *are not preserved* in the IRQ handler!
    So you should make sure that the handler routine does NOT use these registers, or do some sort of saving/restoring yourself
    of the ones that you do need in the IRQ handler.

    It is also advised to not use floating point calculations inside IRQ handler routines.
    Beside them being very slow, there are intricate requirements such as having the
    correct ROM bank enabled to be able to successfully call them (and making sure the correct
    ROM bank is reset at the end of the handler), and the possibility
    of corrupting variables and floating point calculations that are being executed
    in the interrupted main program. These memory locations should be backed up
    and restored at the end of the handler, further increasing its execution time...
