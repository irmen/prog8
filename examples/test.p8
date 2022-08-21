%import textio
%zeropage basicsafe


main {
    sub start() {

        uword slab1 = memory("slab 1", 2000, 0)
        uword slab2 = memory("slab 1", 2000, 0)
        uword slab3 = memory("slab other", 2000, 64)

        txt.print_uwhex(slab1, true)
        txt.print_uwhex(slab2, true)
        txt.print_uwhex(slab3, true)


        ubyte rasterCount = 231

        if rasterCount >= 230
            txt.print("y1")

        if rasterCount ^ $80 >= 230
            txt.print("y2")

        if (rasterCount ^ $80) >= 230
            txt.print("y3")

    }
}
