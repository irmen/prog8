%import textio
%import math
%import cx16logo

; Note: this program is compatible with C64 and CX16.

main {
    sub start() {
        repeat {
            ubyte col = math.rnd() % (txt.DEFAULT_WIDTH-13) + 3
            ubyte row = math.rnd() % (txt.DEFAULT_HEIGHT-7)
            cx16logo.logo_at(col, row)
            txt.plot(col-3, row+7 )
            txt.print("commander x16")
        }
    }
}
