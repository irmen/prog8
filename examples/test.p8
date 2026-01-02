%import textio
%zeropage basicsafe

main {
    sub start()  {
        const long value1, value2, value3 = 255
        const ubyte valueb = 255

        txt.print_l(valueb+1)
        txt.nl()
        txt.print_l(value1+1)
        txt.nl()
        cx16.r0L = value2
    }
}
