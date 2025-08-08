%import textio
%import floats
%option no_sysinit
%zeropage basicsafe


main {
    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

    sub start() {
        ^^Enemy e1 = 30000
        e1.elite=false

        if e1.elite
            txt.print("elite")
        else
            txt.print("pleb")

        e1.elite = true

        if e1.elite
            txt.print("elite")
        else
            txt.print("pleb")
    }
}
