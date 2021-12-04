%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte num_banks = cx16.numbanks()
        txt.print("number of ram banks ")
        txt.print_ub(num_banks)
        txt.print(" = ")
        txt.print_uw($0008*num_banks)
        txt.print("kb\n")
        print_banks()
        cx16.rambank(55)
        cx16.rombank(3)
        print_banks()
    }

    sub print_banks() {
        ubyte rambank = cx16.getrambank()
        ubyte rombank = cx16.getrombank()
        cx16.rombank(0) ; enable kernal
        txt.print("ram bank ")
        txt.print_ub(rambank)
        txt.nl()
        txt.print("rom bank ")
        txt.print_ub(rombank)
        txt.nl()
        cx16.rombank(rombank)
    }
}
