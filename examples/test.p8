%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name1 = "irmen de jong\n"
        str name2 = "123456"
        sys.memcopy(&name2, &name1+5,len(name2))
        txt.print(name1)
        sys.memset(&name1+3, 8, '!')
        txt.print(name1)
        sys.memsetw(&name1+3, 4, $5544)
        txt.print(name1)
        name1 = name2
        txt.print(name1)
        txt.nl()
        ubyte length = string.copy("hello there", name1)
        txt.print_ub(length)
        txt.spc()
        txt.print(name1)
        txt.nl()

;        str name1 = "name1"
;        str name2 = "name2"
;        uword[] @split names = [name1, name2, "name3"]
;        uword[] addresses = [0,0,0]
;        names = [1111,2222,3333]
;
;        for cx16.r0 in names {
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
