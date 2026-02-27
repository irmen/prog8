%import textio
%zeropage basicsafe

main {
    ; Test the routine
    sub start() {
        ; emudbg.reset_cpu_cycles()
        txt.print("zzzzz")
        cbm.CHROUT('$')
        repeat 100 {
            zz(11, 11111111, "hello")
            subje('.')
        }
    }

    sub zz(ubyte sp, long cycles, str arg) {
        cx16.r0++
    }

    asmsub subje(ubyte value @ A) {
        %asm {{
            jsr  cbm.CHROUT
            rts
        }}
    }
}

