%import floats
%import textio
%option no_sysinit
%zeropage basicsafe


main {
    struct Node {
        uword value
        bool flag
        ^^Node next
    }

    sub start() {
        ^^uword ptr = 3000
        ptr^^=9999
        txt.print_uw(peekw(3000))
        txt.spc()
        ptr^^ ++
        txt.print_uw(peekw(3000))
        txt.spc()
        ptr^^ += 123
        txt.print_uw(peekw(3000))
        txt.spc()
        ptr^^ -= 123
        txt.print_uw(peekw(3000))
        txt.spc()
        ptr^^ --
        txt.print_uw(peekw(3000))
        txt.nl()

        ptr^^ ^= $eeee
        ptr^^ = 1111
        ptr^^ *= 5
        cx16.r0 = 2
        ptr^^ *= cx16.r0
        uword @shared wvar = 3
        ptr^^ *= wvar
        wvar = cx16.r0 = 1111
        ptr^^ += cx16.r0
        ptr^^ += wvar
        txt.print_uw(peekw(3000))
        txt.nl()
        ptr^^ *= 4
        ptr^^ /= 4
        ptr^^ += 3
        ptr^^ -= 3
        ptr^^ *= 3
        ptr^^ /= 3
        ^^float fptr = 0
        fptr^^ += 3.0
        fptr^^ -= 3.0
        fptr^^ *= 3.0
        fptr^^ /= 3.0


;        ^^Node nptr = 30000
;        ^^Node nptr2 = Node()
;        ^^Node nptr3 = Node(9999, true, 12345)
;
;        txt.print_bool(nptr2.flag)
;        txt.spc()
;        txt.print_bool(nptr3.flag)
;        txt.spc()
;        txt.print_uw(nptr2.next)
;        txt.spc()
;        txt.print_uw(nptr3.next)
    }
}
