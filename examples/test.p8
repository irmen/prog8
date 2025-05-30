%import floats
%import textio

main {
    ubyte[] array = [11,22,33,44,55]
    float[] flarray = [1.1, 2.2, 3.3, 4.4, 5.5]

    sub start() {
        txt.print_ub(array[2])
        txt.nl()
        cx16.r0L = 2
        txt.print_ub(array[cx16.r0L])
        txt.nl()

        txt.print_f(flarray[2])
        txt.nl()
        cx16.r0L = 2
        txt.print_f(flarray[cx16.r0L])
        txt.nl()

        ^^ubyte bptr = &array[2]
        ^^float fptr = &flarray[2]
        txt.print_ub(bptr[2])
        txt.spc()
        txt.print_ub(bptr[cx16.r0L])
        txt.nl()
        txt.print_f(fptr[2])
        txt.spc()
        txt.print_f(fptr[cx16.r0L])
        txt.nl()
    }
}
