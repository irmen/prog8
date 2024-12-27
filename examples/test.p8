%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool derp

        float @shared f1,f2
        txt.nl()

        cx16.r0 = $aaaa
        cx16.r1 = $2222
        f1 = 10000
        cx16.r0=10000
        ; true false false
        ; true true false
        ; times 2.
        txt.print_bool(f1 > 8000)
        txt.spc()
        txt.print_bool(f1 > 10000)
        txt.spc()
        txt.print_bool(f1 > 20000)
        txt.nl()
        txt.print_bool(f1 >= 8000)
        txt.spc()
        txt.print_bool(f1 >= 10000)
        txt.spc()
        txt.print_bool(f1 >= 20000)
        txt.nl()
        txt.print_bool(cx16.r0 > 8000)
        txt.spc()
        txt.print_bool(cx16.r0 > 10000)
        txt.spc()
        txt.print_bool(cx16.r0 > 20000)
        txt.nl()
        txt.print_bool(cx16.r0 >= 8000)
        txt.spc()
        txt.print_bool(cx16.r0 >= 10000)
        txt.spc()
        txt.print_bool(cx16.r0 >= 20000)
        txt.nl()

        cx16.r0L=0
        derp=true
        if cx16.r0L==0 and derp
            txt.print("fl is 0\n")
        else
            txt.print("fl is not 0\n")
        if cx16.r0L!=0 and derp
            txt.print("fl is not 0\n")
        else
            txt.print("fl is 0\n")

        cx16.r0L = 1
        if cx16.r0L==0 and derp
            txt.print("fl is 0\n")
        else
            txt.print("fl is not 0\n")
        if cx16.r0L!=0 and derp
            txt.print("fl is not 0\n")
        else
            txt.print("fl is 0\n")



        cx16.r0L=99
        derp=true
        if cx16.r0L==99 and derp
            txt.print("fl is 99\n")
        else
            txt.print("fl is not 99\n")
        if cx16.r0L!=99 and derp
            txt.print("fl is not 99\n")
        else
            txt.print("fl is 99\n")

        cx16.r0L = 122
        if cx16.r0L==99 and derp
            txt.print("fl is 99\n")
        else
            txt.print("fl is not 99\n")
        if cx16.r0L!=99 and derp
            txt.print("fl is not 99\n")
        else
            txt.print("fl is 99\n")
    }
}

