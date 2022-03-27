; Internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%import textio

prog8_lib {
    %option force_output

    sub string_contains(ubyte needle, str haystack) -> ubyte {
        txt.print(">>>string elt check: ")
        txt.print_ub(needle)
        txt.spc()
        txt.print_uwhex(haystack, true)
        txt.nl()
        return 0
    }

    sub bytearray_contains(ubyte needle, uword haystack_ptr, ubyte num_elements) -> ubyte {
        txt.print(">>>bytearray elt check: ")
        txt.print_ub(needle)
        txt.spc()
        txt.print_uwhex(haystack_ptr, true)
        txt.spc()
        txt.print_ub(num_elements)
        txt.nl()
        return 0
    }

    sub wordarray_contains(ubyte needle, uword haystack_ptr, ubyte num_elements) -> ubyte {
        txt.print(">>>wordarray elt check: ")
        txt.print_ub(needle)
        txt.spc()
        txt.print_uwhex(haystack_ptr, true)
        txt.spc()
        txt.print_ub(num_elements)
        txt.nl()
        return 0
    }
}
