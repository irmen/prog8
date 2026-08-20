main {

    struct Thing {
        uword port
        ^^Thing next
        bool flag
    }

    sub start() {
        ^^Thing t

        ; this compiles fine:
        bool derp1 = t.next.flag
        ubyte derp2 = t.next.flag as ubyte

        ; TODO fix compiler crash:
        bool derp3 = (t.next).flag
        ubyte derp4 = (t.next).flag as ubyte

        ; TODO fix invalid error messages:
        bool derp5 = (t.port as ^^Thing).flag
        ubyte derp6 = (t.port as ^^Thing).flag as ubyte
    }
}
