%import textio
%import string
%zeropage basicsafe

main {

    sub start() {
        txt.chrout('!')
        txt.print("test")
        txt.nl()
;        uword seconds_uword = 1
;        uword remainder = seconds_uword % $0003 ==0
;        txt.print_uw(remainder)
;
;        blerp()
    }

    sub blerp() {
        %ir {{
_xxx:
        loadr  r2,r3
_yyy:
        loadr  r3,r4
        return
        }}
    }
}

