; %import textio

main {

    sub start() {
        ; should get a return after the nop
        %ir {{
            nop
        }}

;        ubyte aa = 42
;        ubyte bb = 99
;        aa += bb
;        txt.print_ub(aa)
    }
}
