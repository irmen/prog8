%import c64utils
%import c64lib
%zeropage basicsafe

    ; @todo see problem in looplabelproblem.p8


~ main {

    sub start() {
        c64utils.set_rasterirq(220)     ; enable animation

        uword offs=0
        while(true) {
            uword z=1
            for ubyte x in 0 to 200 {
                @(z*($0400+offs)) = lsb(offs+x)
                offs += 1
                if offs > 40*25
                    offs=0
            }
        }
    }
}


~ irq {

    sub irq() {
        c64.EXTCOL = X
    }

}
