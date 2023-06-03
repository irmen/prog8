%import textio
%zeropage basicsafe

main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"

        uword[] @split names = [name1, name2, "name3"]

        uword ww
;        for ww in names {
;            txt.print(ww)
;            txt.spc()
;        }
;        txt.nl()
        ubyte idx=1
        names[idx] = $20ff
        txt.print_uwhex(names[1], true)
        names[idx]++
        txt.print_uwhex(names[1], true)
        names[idx]--
        txt.print_uwhex(names[1], true)

        names = [1111,2222,3333]
        for ww in names {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.nl()
        txt.print("end.")
    }
}

