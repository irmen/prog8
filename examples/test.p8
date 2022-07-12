%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte value1 = %1110
        ubyte value2 = %1111

        bool[2] @shared barr = [true, false]

;        if value1 and value2        ; TODO value1 isn't converted to bool in 6502 preprocess
;            txt.print("ok")
;        else
;            txt.print("fail!")
;        txt.nl()

        if value1 and value2!=255       ; TODO value1 isn't converted to bool in 6502 preprocess
            txt.print("ok")
        else
            txt.print("fail!")
    }
}
