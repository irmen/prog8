; Internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%import textio

prog8_lib {
    %option force_output

    sub string_contains(ubyte needle, str haystack) -> ubyte {
        repeat {
            if @(haystack)==0
                return false
            if @(haystack)==needle
                return true
            haystack++
        }
    }

    sub bytearray_contains(ubyte needle, uword haystack_ptr, ubyte num_elements) -> ubyte {
        haystack_ptr--
        while num_elements {
            if haystack_ptr[num_elements]==needle
                return true
            num_elements--
        }
        return false
    }

    sub wordarray_contains(ubyte needle, uword haystack_ptr, ubyte num_elements) -> ubyte {
        haystack_ptr += (num_elements-1) * 2
        while num_elements {
            if peekw(haystack_ptr)==needle
                return true
            haystack_ptr -= 2
            num_elements--
        }
        return false
    }
}
