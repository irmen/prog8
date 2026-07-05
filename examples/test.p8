%import textio

main {
    sub start() {
        ^^ubyte @shared message
        ubyte @shared index
        message += index       ; TODO fix register double type error (m68k target)

        void print1("done.\n")

       ;; long @shared zz = sys.progend()
;        txt.print("progend: ")
;        txt.print_ulhex(zz, true)
;        txt.nl()

;        print1("first: Hello from Prog8 on a m68000 system!\n")
;        print1b("first (b): Hello from Prog8 on a m68000 system!\n")
;        print2("second: Hello from Prog8 on a m68000 system!\n")
    }

    sub print1(str msg) -> str {
        while @(msg)!=0 {
            txt.chrout(@(msg))
            msg++
        }

        return 9999
    }

;    sub print1b(str msg) {
;        long ii
;        while msg[ii] != 0 {
;            txt.chrout(msg[ii])     ; TODO fix pointer indexing ???
;            ii++
;        }
;    }
;
;    sub print2(^^ubyte msg) {
;        while @(msg)!=0 {
;            txt.chrout(@(msg))
;            msg++
;        }
;    }
;
;    sub hurrah() -> ubyte, uword, long {
;        ubyte @shared x,y
;        uword z
;        long ll
;        x+=y
;        z+=x
;        ll+=z
;        return x,z,ll
;    }
}
