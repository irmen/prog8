%import textio
%zeropage basicsafe

main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"

        uword[]  names = [name1, name2, "name3"]

        uword ww
;        for ww in names {
;            txt.print(ww)
;            txt.spc()
;        }
;        txt.nl()
        ubyte idx=1
        names[idx] = 2000
        txt.print_uw(names[1])
        names[idx]++
        txt.print_uw(names[1])
        names[idx]--
        txt.print_uw(names[1])

;        names = [1111,2222,3333]
;        for ww in names {
;            txt.print_uw(ww)
;            txt.spc()
;        }
;        txt.nl()
        txt.print("end.")
    }
}

