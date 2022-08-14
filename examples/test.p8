%import textio
%zeropage basicsafe


main {
    sub start() {

        ubyte rasterCount = 231

        if rasterCount >= 230
            txt.print("y1")

        if rasterCount ^ $80 >= 230
            txt.print("y2")

        if (rasterCount ^ $80) >= 230
            txt.print("y3")

    }
}
