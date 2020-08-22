%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    ; todo make it possible to use cpu opcodes as varnames such as 'nop'

    sub start() {

        byte ub
        word uw
        ubyte x = 1
        ubyte z = 0

        ubyte[] array = [1,2,3]

        ubyte i
        ub = %00100001
        for i in 0 to 9 {
            c64scr.print_ubbin(ub as ubyte, true)
            c64.CHROUT('\n')
            ;ub <<= 1
            ;ub <<= x
            ub <<= (z+1)
        }
        c64.CHROUT('\n')
        ;ub = %11000110 ; -123
        ub = -123
        for i in 0 to 9 {
            c64scr.print_ubbin(ub as ubyte, true)
            c64.CHROUT('\n')
            ;ub >>= 1
            ;ub >>= x
            ub >>= (z+1)
        }

        uw = %0011100000000011
        for i in 0 to 17 {
            c64scr.print_uwbin(uw as uword, true)
            c64.CHROUT('\n')
            ;uw <<= 1
            ;uw <<= x
            uw <<= (z+1)
        }
        c64.CHROUT('\n')
        uw = -12345
        ;uw = %1110000011000100
        for i in 0 to 17 {
            c64scr.print_uwbin(uw as uword, true)
            c64.CHROUT('\n')
            ;uw >>= 1
            ;uw >>= x
            uw >>= (z+1)
        }
    }
}
