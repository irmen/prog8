%import textio
%zeropage basicsafe

main {
    str s1 = "Irmen_"
    str s2 = @"IRMEN_"
    str s3 = sc:"IRMEN_"
    str s4 = iso:"Käse, Straße"

    sub start() {
        txt.iso()
        ; txt.lowercase()
        txt.nl()
        txt.nl()
        txt.nl()
        txt.nl()
        txt.nl()
        txt.print(s1)
        txt.nl()
        txt.print(s2)
        txt.nl()
        txt.print(s3)
        txt.nl()
        txt.print(s4)
        txt.nl()

        sc(1, s1)
        sc(2, s2)
        sc(3, s3)
        sc(4, s4)
    }

    sub sc(ubyte row, str text) {
        uword addr = 1024+row*40
        ubyte ix = 0
        ubyte ss
        repeat {
            ss = text[ix]
            if not ss
                return
            @(addr) = ss
            addr++
            ix++
        }
    }
}
