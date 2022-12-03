%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        ; TODO ALSO TEST AS GLOBALS
        ubyte @requirezp zpvar = 10
        ubyte @zp zpvar2 = 20
        uword empty
        ubyte[10] bssarray
        uword[10] bsswordarray
        ubyte[10] nonbssarray = 99
        str name="irmen"

        txt.print("10 ")
        txt.print_ub(zpvar)
        txt.nl()
        zpvar++

        txt.print("20 ")
        txt.print_ub(zpvar2)
        txt.nl()
        zpvar2++

        txt.print("0 ")
        txt.print_uw(empty)
        txt.nl()
        empty++

        txt.print("0 ")
        txt.print_ub(bssarray[1])
        txt.nl()
        bssarray[1]++

        txt.print("0 ")
        txt.print_uw(bsswordarray[1])
        txt.nl()
        bsswordarray[1]++

        txt.print("99 ")
        txt.print_ub(nonbssarray[1])
        txt.nl()
        nonbssarray[1]++

        txt.print("r ")
        txt.chrout(name[1])
        txt.nl()
        name[1] = (name[1] as ubyte +1)

        txt.print("try running again.\n")
    }
}
