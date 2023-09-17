%import textio
%import floats

%zeropage basicsafe

main {
    sub start() {
        ubyte[] barray = [11,22,33]
        uword[] warray = [$1234,$5678,$abcd]
        uword[] @split split_warray = [$1234,$5678,$abcd]
        float[] float_array = [11.11,22.22,33.33]

        txt.print("incr of 1: ")
        txt.print_uw(&barray)
        txt.spc()
        txt.print_uw(&barray[0])
        txt.spc()
        txt.print_uw(&barray[1])
        txt.spc()
        txt.print_uw(&barray[2])
        txt.nl()

        txt.print("incr of 2: ")
        txt.print_uw(&warray)
        txt.spc()
        txt.print_uw(&warray[0])
        txt.spc()
        txt.print_uw(&warray[1])
        txt.spc()
        txt.print_uw(&warray[2])
        txt.nl()

        txt.print("incr of 1: ")
        txt.print_uw(&split_warray)
        txt.spc()
        txt.print_uw(&split_warray[0])
        txt.spc()
        txt.print_uw(&split_warray[1])
        txt.spc()
        txt.print_uw(&split_warray[2])
        txt.nl()

        txt.print("incr of 4 or 5: ")
        txt.print_uw(&float_array)
        txt.spc()
        txt.print_uw(&float_array[0])
        txt.spc()
        txt.print_uw(&float_array[1])
        txt.spc()
        txt.print_uw(&float_array[2])
        txt.nl()
    }
}
