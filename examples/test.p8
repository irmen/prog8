%import textio
%import floats
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
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
;        if arg==222
;            return 0
        txt.print("allocate ")
        txt.print_uw(4000+arg)
        txt.nl()
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
