%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        txt.lowercase()

        diskio.directory_dirs()
        txt.nl()

        diskio.directory_files()
        txt.nl()

        diskio.lf_start_list_dirs(0)
        while diskio.lf_next_entry() {
            txt.print(diskio.list_filetype)
            txt.spc()
            txt.spc()
            txt.print(diskio.list_filename)
            txt.nl()
        }
        diskio.lf_end_list()

        txt.nl()
        txt.nl()
        diskio.lf_start_list_files(0)
        while diskio.lf_next_entry() {
            txt.print(diskio.list_filetype)
            txt.spc()
            txt.spc()
            txt.print(diskio.list_filename)
            txt.nl()
        }
        diskio.lf_end_list()
    }
}


/*

%address  $A000
%memtop   $C000
%output   library

%import textio


main {
    ; Create a jump table as first thing in the library.
    uword[] @shared @nosplit jumptable = [
        ; NOTE: the compiler has inserted a single JMP instruction at the start of the 'main' block, that jumps to the start() routine.
        ;       This is convenient because the rest of the jump table simply follows it,
        ;       making the first jump neatly be the required initialization routine for the library (initializing variables and BSS region).
        ;       btw, $4c = opcode for JMP.
        $4c00, &library.func1,
        $4c00, &library.func2,
    ]

    sub start() {
        ; has to be here for initialization
        txt.print("lib initialized\n")
    }
}


library {
    sub func1() {
        txt.print("lib func 1\n")
    }

    sub func2() {
        txt.print("lib func 2\n")
    }
}
*/
