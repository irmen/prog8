%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        if diskio.load_raw("fselector-a000.bin", $a000) != 0 {
            fselector.init()
            fselector.config(8, fselector.TYPE_ALL)
            uword filename = fselector.select("*")
            txt.print("\n\n\n\n\n")

            if filename!=0 {
                txt.print("selected: ")
                txt.print(filename)
                txt.nl()
            } else {
                txt.print("nothing selected or error.\n")
            }
        }
    }
}

fselector {
    ; The library uses/modifies ZERO PAGE LOCATIONS: R0-R5,R15 ($02-$0d and $20-$21)

    const ubyte TYPE_ALL = 0
    const ubyte TYPE_FILES = 1
    const ubyte TYPE_DIRS = 2

    ; initialize the library, required as first call
    extsub $a000 = init() clobbers(A)

    ; what entry types should be displayed (default=all)
    extsub $a004 = config(ubyte drivenumber @A, ubyte types @Y) clobbers(A)

    ; configure the position and appearance of the dialog
    extsub $a008 = config_appearance(ubyte column @R0, ubyte row @R1, ubyte max_entries @R2, ubyte normalcolors @R3, ubyte selectedcolors @R4) clobbers(A)

    ; show the file selector dialog. Normal pattern would be "*" to include everything.  Returns the selected entry name, or 0 if error or nothing selected.
    extsub $a00c = select(str pattern @AY) clobbers(X) -> uword @AY
}
