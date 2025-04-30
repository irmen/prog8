%zeropage basicsafe
%import textio
main {
    sub start() {
        byte @shared a = -1
        byte @shared b = -15
        ubyte @shared ub = 2
        const byte ca = -1
        const byte cb = -15
        const ubyte cub = 2

        txt.print_ub( a & b )
        txt.spc()
        txt.print_ub( a | b )
        txt.spc()
        txt.print_ub( a ^ b )
        txt.spc()
        txt.print_b( a << ub )
        txt.spc()
        txt.print_b( a >> ub )
        txt.nl()
        txt.print_ub( ca & cb )
        txt.spc()
        txt.print_ub( ca | cb )
        txt.spc()
        txt.print_ub( ca ^ cb )
        txt.spc()
        txt.print_b( ca << cub )
        txt.spc()
        txt.print_b( ca >> cub )
        txt.nl()

        word @shared aw = -1
        word @shared bw = -15
        uword @shared uw = 2
        const word caw = -1
        const word cbw = -15
        const uword cuw = 2

        txt.print_uw( aw & bw )
        txt.spc()
        txt.print_uw( aw | bw )
        txt.spc()
        txt.print_uw( aw ^ bw )
        txt.nl()
        txt.print_uw( caw & cbw )
        txt.spc()
        txt.print_uw( caw | cbw )
        txt.spc()
        txt.print_uw( caw ^ cbw )
        txt.nl()
        txt.print_w( cbw << cuw )
        txt.spc()
        txt.print_w( cbw >> cuw )
        txt.nl()
    }
}
