%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte xx = wobwob()
        txt.print_ub(xx)
        uword yy = wobwob2()
        txt.print_uw(yy)

        asmsub wobwob() -> bool @Pc {
            %asm {{
                sec
                rts
            }}
        }

        asmsub wobwob2() -> bool @Pc {
            %asm {{
                clc
                rts
            }}
        }
    }
}
