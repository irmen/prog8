%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str name = "irmen@de@jong"
        cx16.r0L = findstr(name, "de-")
        if_cs {
            txt.print("found1. error. ")
        } else {
            txt.print("not found1. ok ")
        }
        txt.print_ub(cx16.r0L)
        txt.nl()
        txt.nl()
        cx16.r0L = findstr(name, "de@")
        if_cs {
            txt.print("found2 (6?). ")
        } else {
            txt.print("not found2. error ")
        }
        txt.print_ub(cx16.r0L)
        txt.nl()
        txt.nl()
        cx16.r0L = findstr(name, "irmen@de@jong")
        if_cs {
            txt.print("found3 (0?). ")
        } else {
            txt.print("not found3. error ")
        }
        txt.print_ub(cx16.r0L)
        txt.nl()
        txt.nl()
        cx16.r0L = findstr(name, "irmen@de@jong1")
        if_cs {
            txt.print("found4. error. ")
        } else {
            txt.print("not found4. ok ")
        }
        txt.print_ub(cx16.r0L)
        txt.nl()
        txt.nl()
        cx16.r0L = findstr(name, "jong")
        if_cs {
            txt.print("found5 (9?). ")
        } else {
            txt.print("not found5. error ")
        }
        txt.print_ub(cx16.r0L)
        txt.nl()
        txt.nl()
    }

    sub findstr(str haystack, str needle) -> ubyte {
        ; searches for needle in haystack.
        ; returns index in haystack where it first occurs, and Carry set,
        ; or if needle doesn't occur in haystack it returns Carry clear and 255 (an invalid index.)
        txt.print_uwhex(haystack, true)
        txt.spc()
        txt.print(haystack)
        txt.nl()
        cx16.r2L = string.length(haystack)
        cx16.r3L = string.length(needle)
        if cx16.r3L <= cx16.r2L {
            cx16.r2L = cx16.r2L-cx16.r3L+1
            repeat cx16.r2L {
                if string.startswith(haystack, needle) {
                    sys.set_carry()
                    return 13 as ubyte
                }
                haystack++
            }
        }
        sys.clear_carry()
        return 255
    }
}
