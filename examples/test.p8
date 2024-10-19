%import textio
%import floats
%option no_sysinit
%zeropage basicsafe



main {
    sub start() {

        ubyte @shared c=99
        if c>100
            cx16.r0L++
        cx16.r0L = if (c>100)  2 else (3)
        txt.print_ub(if (c>100)  2 else 3)
        txt.nl()
        txt.print_ub(if (c<100)  6 else 7)
        txt.nl()

        float @shared fl=99.99
        floats.print(if (c>100)  2.22 else 3.33)
        txt.nl()
        floats.print(if (c<100)  6.66 else 7.77)
        txt.nl()

        uword res1 = allocate(111)
        defer deallocate(res1)
        uword res2 = allocate(222)
        if res2==0
            return
        defer deallocate(res2)

        if not process1(res1, res2)
            return
        if not process2(res1, res2)
            return
    }

    sub allocate(uword arg) -> uword {
        return 4000+arg
    }

    sub deallocate(uword arg) {
        txt.print("dealloc ")
        txt.print_uw(arg)
        txt.nl()
    }

    sub process1(uword arg1, uword arg2) -> bool {
        txt.print("process1 ")
        txt.print_uw(arg1)
        txt.spc()
        txt.print_uw(arg2)
        txt.nl()
        return true
    }

    sub process2(uword arg1, uword arg2) -> bool {
        txt.print("process2 ")
        txt.print_uw(arg1)
        txt.spc()
        txt.print_uw(arg2)
        txt.nl()
        return true
    }
}
