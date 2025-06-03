%import textio
%import strings
%zeropage basicsafe
%encoding petscii

main {
    sub start() {
        str textiso = iso:"first\x0asecond\x0athird\x0a"
        str textpetscii = petscii:"first\nsecond\nthird\n"

        txt.print("    iso: ")
        dump(textiso)
        txt.nl()
        cx16.r0L, void = strings.find(textiso, '\n')
        txt.print_ub(cx16.r0L)
        txt.spc()
        cx16.r0L, void = strings.find_eol(textiso)
        txt.print_ub(cx16.r0L)
        txt.nl()

        txt.print("petscii: ")
        dump(textpetscii)
        txt.nl()
        cx16.r0L, void = strings.find(textpetscii, '\n')
        txt.print_ub(cx16.r0L)
        txt.spc()
        cx16.r0L, void = strings.find_eol(textpetscii)
        txt.print_ub(cx16.r0L)
        txt.nl()
    }

    sub dump(uword ptr) {
        while @(ptr)!=0 {
            txt.print_ubhex(@(ptr), false)
            txt.spc()
            ptr++
        }
    }
}
