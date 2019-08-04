%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    sub start() {
        ubyte ub=0
        while ub<30 {
            ub++
            if ub < 5
                continue
            c64scr.print_ub(ub)
            c64.CHROUT(',')
            if ub >= 10
                break
        }
        c64.CHROUT('\n')

        ub=0
        repeat {
            ub++
            if ub < 5
                continue
            c64scr.print_ub(ub)
            c64.CHROUT(',')
            if ub>=10
                break
        } until ub>30
        c64.CHROUT('\n')

        for ub in 1 to 30 {
            if ub < 5
                continue
            c64scr.print_ub(ub)
            c64.CHROUT(',')
            if ub >=10
                break
        }
        c64.CHROUT('\n')
    }
}
