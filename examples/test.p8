%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        uword uw = $ab34
        str name = "irmen de jong"

        c64scr.print_ub(len(name))
        c64.CHROUT('\n')
        c64scr.print_ub(strlen(name))
        c64.CHROUT('\n')
        c64scr.print(name)
        c64.CHROUT('\n')
        name[6] = 0
        c64scr.print_ub(strlen(name))
        c64.CHROUT('\n')
        c64scr.print(name)
        c64.CHROUT('\n')

    }
}
