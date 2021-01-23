%import textio
%import diskio
%import string
%import floats
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {

        struct SaveData {
            ubyte galaxy
            ubyte planet
            uword cash
            float flt
            ubyte fuel
        }

        SaveData data

        txt.print("size of struct: ")
        txt.print_ub(sizeof(SaveData))
        txt.chrout(';')
        txt.print_ub(sizeof(data))
        txt.chrout('\n')

        txt.print("offset of galaxy: ")
        txt.print_ub(offsetof(data.galaxy))
        txt.chrout('\n')

        txt.print("offset of planet: ")
        txt.print_ub(offsetof(data.planet))
        txt.chrout('\n')

        txt.print("offset of cash: ")
        txt.print_ub(offsetof(data.cash))
        txt.chrout('\n')

        txt.print("offset of flt: ")
        txt.print_ub(offsetof(data.flt))
        txt.chrout('\n')

        txt.print("offset of fuel: ")
        txt.print_ub(offsetof(data.fuel))
        txt.chrout('\n')

    }
}
