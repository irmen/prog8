%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"
        uword[] @split names = [name1, name2, "name3"]
        uword[] addresses = [0,1,2]
        names = [1111,2222,3333]

        for cx16.r0 in names {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()

;        for cx16.r0 in addresses {
;            txt.print_uw(cx16.r0)
;            txt.spc()
;        }
;        txt.nl()
;
;        addresses = names
;
;        for cx16.r0 in addresses {
;            txt.print_uw(cx16.r0)
;            txt.spc()
;        }
;        txt.nl()
    }
}
