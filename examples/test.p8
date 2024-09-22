%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        float fl = 5.23
        fl = floats.ceil(fl)
        floats.print(fl)
        txt.nl()
        fl = 5.23
        fl = floats.floor(fl)
        floats.print(fl)
        txt.nl()
    }
}
