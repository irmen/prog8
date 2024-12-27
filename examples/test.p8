%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool derp

        float @shared f1,f2
        txt.nl()


        derp = cx16.r0L as bool
        cx16.r0++
        derp = cx16.r0 as bool

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

