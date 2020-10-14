%import textio
%import cx16logo

; Note: this program is compatible with C64 and CX16.

main {
    sub start() {
        repeat {
            ubyte col = rnd() % (txt.DEFAULT_WIDTH-12) + 3
            ubyte row = rnd() % (txt.DEFAULT_HEIGHT-7)
            cx16logo.logo_at(col, row)
            txt.plot(col-3, row+7 )
            txt.print("commander x16")
        }
    }
}

