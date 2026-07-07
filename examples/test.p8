%import textio

main {
    sub start() {

        txt.print("progstart:")
        txt.print_ulhex(sys.progstart(), true)
        txt.nl()
        txt.print("progend: ")
        txt.print_ulhex(sys.progend(), true)
        txt.nl()
        txt.print("start: ")
        txt.print_ulhex(&start, true)
        txt.nl()

;        print1("first: Hello from Prog8 on a m68000 system!\n")
;        print1b("first (b): Hello from Prog8 on a m68000 system!\n")
;        print2("second: Hello from Prog8 on a m68000 system!\n")
    }

;    sub print1(str msg) -> str {
;        while @(msg)!=0 {
;            txt.chrout(@(msg))
;            msg++
;        }
;
;        return 9999
;    }

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
