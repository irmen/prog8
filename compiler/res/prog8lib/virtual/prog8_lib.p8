; Internal library routines - always included by the compiler

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

    sub string_compare(str st1, str st2) -> byte {
        ; Compares two strings for sorting.
        ; Returns -1 (255), 0 or 1 depending on wether string1 sorts before, equal or after string2.
        ; Note that you can also directly compare strings and string values with eachother using
        ; comparison operators ==, < etcetera (it will use strcmp for you under water automatically).
        %asm {{
            loadm.w r0,prog8_lib.string_compare.st1
            loadm.w r1,prog8_lib.string_compare.st2
            syscall 29
            return
        }}
    }

}

