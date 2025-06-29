Various examples for configurable target machines, such as the NEO6502, Atari 800,
Foenix F256, and "tiny" example configurations for the X16 or C64 that provide
more example materials.

Look in the Makefile to see how to build or run the various programs.

The user 'adiee5' has been working on a Nintendo Entertainment System (NES) compilation target
and example program, you can find those efforts here on GitHub: https://github.com/adiee5/prog8-nes-target
Note that the NES is a very alien architecture for Prog8 still and the support is very limited
(for example, prog8 is not aware that the program code usually is going to end up in a ROM cartridge,
and currently still generates code that might not work in ROM.)
