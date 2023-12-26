; Internal library routines - always included by the compiler

prog8_lib {
    %option no_symbol_prefixing, ignore_unused

	%asminclude "library:prog8_lib.asm"
	%asminclude "library:prog8_funcs.asm"
}
