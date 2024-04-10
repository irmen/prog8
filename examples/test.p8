%import textio
%zeropage basicsafe
%option no_sysinit

main {
    ubyte[255] array1
    ubyte[255] array2
    uword block1 = memory("b1", 6000 ,0)
    uword block2 = memory("b2", 6000 ,0)

    sub start() {
        cbm.SETTIM(0,0,0)

        repeat 2000 {
            sys.memcopy(array1, array2, sizeof(array1))
        }

        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        cbm.SETTIM(0,0,0)

        repeat 2000 {
            cx16.memory_copy(array1, array2, sizeof(array1))
        }

        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        cbm.SETTIM(0,0,0)

        repeat 100 {
            sys.memcopy(block1, block2, 6000)
        }

        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        cbm.SETTIM(0,0,0)

        repeat 100 {
            cx16.memory_copy(block1, block2, 6000)
        }

        txt.print_uw(cbm.RDTIM16())
        txt.nl()
    }
}

