%import math
%import textio
%zeropage dontuse

main {
    sub start() {
        uword @shared x = 65535
        word @shared y = x as word
        txt.print_w(y)
        txt.nl()
        txt.print_w(x as word)
        txt.nl()

        word @shared x2 = -1
        uword @shared y2 = x2 as uword
        txt.print_uw(y2)
        txt.nl()
        txt.print_uw(x2 as uword)
        txt.nl()

        txt.print_uw(shiftluw1())
    }

;    sub shiftluw1() -> uword {
;        uword q = $a49f
;        return (q << 1) & 65535 as uword
;    }

}
