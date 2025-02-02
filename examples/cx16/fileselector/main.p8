%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        if diskio.load("fselector.bin", 0) != 0 {
            fselector.init()
            ;; fselector.config_types(fselector.TYPE_ALL)
            uword filename = fselector.select("*")
            txt.print("\n\n\n\n\nselected: ")
            txt.print(filename)
            txt.nl()
        }
    }
}

fselector {
    const ubyte TYPE_ALL = 0
    const ubyte TYPE_FILES = 1
    const ubyte TYPE_DIRS = 2

    ; initialize the library, required as first call
    extsub $a000 = init() clobbers(A)

    ; what entry types should be displayed (default=all)
    extsub $a004 = config_types(ubyte types @A) clobbers(A)

    ; configure the position and appearance of the dialog
    extsub $a008 = config_appearance(ubyte column @R0, ubyte row @R1, ubyte max_entries @R2, ubyte normalcolors @R3, ubyte selectedcolors @R4) clobbers(A)

    ; show the file selector dialog. Normal pattern would be "*" to include everything.
    extsub $a00c = select(str pattern @AY) clobbers(X) -> uword @AY
}
