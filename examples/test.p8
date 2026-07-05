%import textio

main {
    sub start() {
        ^^ubyte @shared message
        long @shared index
        ubyte char = @(message+index)

       ;; long @shared zz = sys.progend()
;        txt.print("progend: ")
;        txt.print_ulhex(zz, true)
;        txt.nl()

;        print1("first: Hello from Prog8 on a m68000 system!\n")
;        print1b("first (b): Hello from Prog8 on a m68000 system!\n")
;        print2("second: Hello from Prog8 on a m68000 system!\n")
    }

;    sub print1(str msg) {
;        while @(msg)!=0 {
;            txt.chrout(@(msg))
;            msg++
;        }
;    }
;
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
