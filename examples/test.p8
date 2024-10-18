%import textio
%import floats
%option no_sysinit
%zeropage basicsafe



main {
    sub start() {
        for cx16.r0L in 0 to 10 {
            defer txt.print("end!!\n")
        }
        txt.print("old way:\n")
        void oldway()
        txt.print("\nnew way:\n")
        void newway()
    }

    sub oldway() -> bool {
        uword res1 = allocate(111)
        if res1==0
            return false

        uword res2 = allocate(222)
        if res2==0 {
            deallocate(res1)
            return false
        }

        if not process1(res1, res2) {
            deallocate(res1)
            deallocate(res2)
            return false
        }
        if not process2(res1, res2) {
            deallocate(res1)
            deallocate(res2)
            return false
        }

        deallocate(res1)
        deallocate(res2)
        return true
    }

    sub newway() -> bool {
        uword res1 = allocate(111)
        if res1==0
            return false
        defer {
            deallocate(res1)
        }

        uword res2 = allocate(222)
        if res2==0
            return false
        defer {
            deallocate(res2)
        }

        if not process1(res1, res2)
            return false
        if not process2(res1, res2)
            return false

        return true
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
