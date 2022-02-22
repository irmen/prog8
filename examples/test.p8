%import textio
%zeropage kernalsafe

main {
    sub start() {
        ubyte[20] array1 = [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]
        ubyte[20] array2 = [2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2]

        sys.set_irqd()

        cx16.rombank(9)
        sys.memset(array1, 20, 255)

        cx16.rombank(0)
        ubyte ii
        for ii in array1 {
            txt.print_ubhex(ii, false)
            txt.spc()
        }
        txt.nl()
        for ii in array2 {
            txt.print_ubhex(ii, false)
            txt.spc()
        }
        txt.nl()

        cx16.rombank(9)
        sys.memset(array1, 20, 255)
        array2=array1

        cx16.rombank(0)
        for ii in array1 {
            txt.print_ubhex(ii, false)
            txt.spc()
        }
        txt.nl()
        for ii in array2 {
            txt.print_ubhex(ii, false)
            txt.spc()
        }
        txt.nl()

        cx16.rombank(4)

        repeat {
        }
    }
}
