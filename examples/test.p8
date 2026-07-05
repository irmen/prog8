%import textio

main {
    sub start() {

        long @shared zz = sys.progend()
        ubyte a
        uword w
        a,w, zz = hurrah()

        ubyte[] array = [1,2,3]
        array[a+1] += 42
        a = array[a+1]

        txt.chrout('a')
        txt.chrout('b')
        txt.chrout('c')
        txt.chrout('d')
        txt.chrout('\n')

        print1("first: Hello from Prog8 on a m68000 system!\n")
        print1b("first (b): Hello from Prog8 on a m68000 system!\n")
        print2("second: Hello from Prog8 on a m68000 system!\n")
    }

    sub print1(str msg) {
        while @(msg)!=0 {
            txt.chrout(@(msg))
            msg++
        }
    }

    sub print1b(str msg) {
        ubyte ii
        while msg[ii] != 0 {
            txt.chrout(msg[ii+4])     ; TODO fix pointer indexing here it uses longs but ii is a word
            ii++
        }
    }

    sub print2(^^ubyte msg) {
        while @(msg)!=0 {
            txt.chrout(@(msg))
            msg++
        }
    }

    sub hurrah() -> ubyte, uword, long {
        ubyte @shared x,y
        uword z
        long ll
        x+=y
        z+=x
        ll+=z
        return x,z,ll
    }
}
