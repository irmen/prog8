%import textio
%option no_sysinit
%zeropage dontuse

main {
    sub start() {

        cx16.r0 = cx16.r1 = cx16.r2 = cx16.r3 = %0001111_11000011

        cx16.r0 *= $0080
        txt.print("goede antwoord:\n")
        txt.print_uwbin(cx16.r0, true)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()

        %asm {{
            lda  cx16.r1H
            lsr  a
            php     ; save carry
            lda  cx16.r1L
            sta  cx16.r1H
            lda  #0
            sta  cx16.r1L
            plp     ; restore carry
            ror  cx16.r1H
            ror  cx16.r1L
        }}
        txt.print("antwoord 2:\n")
        txt.print_uwbin(cx16.r1, true)
        txt.spc()
        txt.print_uw(cx16.r1)
        txt.nl()
        txt.nl()
        txt.print("antwoord 3:\n")
        txt.print_uwbin(cx16.r2 << 7, true)
        txt.spc()
        txt.print_uw(cx16.r2 << 7)
        txt.nl()
        txt.nl()



    }
}
