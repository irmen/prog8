main {

    struct Thing {
        uword port
        ^^Thing next
        bool flag
    }

    sub start() {
        ^^Thing @shared t

        ; this compiles fine:
        bool @shared derp1 = t.next.flag
        ubyte @shared derp2 = t.next.flag as ubyte

        bool @shared derp3 = (t.next).flag
        ubyte @shared derp4 = (t.next).flag as ubyte

        bool @shared derp5 = (t.port as ^^Thing).flag
        ubyte @shared derp6 = (t.port as ^^Thing).flag as ubyte
    }
}
