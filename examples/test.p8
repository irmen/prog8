%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        float fl1 = 999.8877
        float fl2 = sqrt(fl1) * fl1 + fl1*33.44
        floats.print_f(fl2)
        txt.nl()
        fl1=0
        floats.print_f(fl1)
    }
}
