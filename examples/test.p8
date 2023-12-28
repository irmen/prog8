%import floats
%import math
%import string
%zeropage basicsafe

main {
    sub start() {
        float fl = 1.2  ; no other assignments
        cx16.r0L = string.isdigit(math.diff(119, floats.floor(floats.deg(fl)) as ubyte))
        cx16.r1L = string.isletter(math.diff(119, floats.floor(floats.deg(1.2)) as ubyte))
    }
}
