; any() and all()  checks on arrays/memory buffers.
; These were builtin functions in older versions of the language.

%option no_symbol_prefixing, ignore_unused

anyall {
    sub any(uword arrayptr, uword num_elements) -> bool {
        ; -- returns true if any byte in the array is not zero.
        cx16.r1 = arrayptr
        if msb(num_elements)==0 {
            for cx16.r0L in 0 to lsb(num_elements)-1 {
                if cx16.r1[cx16.r0L]!=0
                    return true
            }
            return false
        }
        repeat num_elements {
            if @(cx16.r1)!=0
                return true
            cx16.r1++
        }
        return false
    }

    sub all(uword arrayptr, uword num_elements) -> bool {
        ; -- returns true if all bytes in the array are not zero.
        cx16.r1 = arrayptr
        if msb(num_elements)==0 {
            for cx16.r0L in 0 to lsb(num_elements)-1 {
                if cx16.r1[cx16.r0L]==0
                    return false
            }
            return true
        }
        repeat num_elements {
            if @(cx16.r1)==0
                return false
            cx16.r1++
        }
        return true
    }

    sub anyw(uword arrayptr, uword num_elements) -> bool {
        ; -- returns true if any word in the array is not zero.
        ;    TODO FIX: doesn't work on split arrays. Just always test every byte !
        cx16.r1 = arrayptr
        if msb(num_elements)==0 {
            repeat lsb(num_elements) {
                if peekw(cx16.r1)!=0
                    return true
                cx16.r1+=2
            }
            return false
        }
        repeat num_elements {
            if peekw(cx16.r1)!=0
                return true
            cx16.r1+=2
        }
        return false
    }

    sub allw(uword arrayptr, uword num_elements) -> bool {
        ; -- returns true if all words in the array are not zero.
        ;    TODO FIX: doesn't work on split arrays. Just always test every byte !
        cx16.r1 = arrayptr
        if msb(num_elements)==0 {
            repeat lsb(num_elements) {
                if peekw(cx16.r1)==0
                    return false
                cx16.r1+=2
            }
            return true
        }
        repeat num_elements {
            if peekw(cx16.r1)==0
                return false
            cx16.r1+=2
        }
        return true
    }
}
