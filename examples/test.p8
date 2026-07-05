%import textio

main {
    sub start() {
        txt.chrout('a')
        txt.chrout('b')
        txt.chrout('c')
        txt.chrout('d')
        txt.chrout('\n')

        print1("first: Hello from Prog8 on a m68000 system!\n")
        ; TODO print1b("first (b): Hello from Prog8 on a m68000 system!\n")
        print2("second: Hello from Prog8 on a m68000 system!\n")
    }

    sub print1(str msg) {
        while @(msg)!=0 {
            txt.chrout(@(msg))
            msg++
        }
    }

    sub print1b(str msg) {
        word ii
        while msg[ii] != 0 {
            txt.chrout(msg[ii])     ; TODO fix pointer indexing here it uses longs but ii is a word
            ii++
        }
    }

    sub print2(^^ubyte msg) {
        while @(msg)!=0 {
            txt.chrout(@(msg))
            msg++
        }
    }
}
