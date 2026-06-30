; Wolfram's Cellular Automatons.

%import math
%import conv
%import textio
%option no_sysinit
%zeropage basicsafe
%encoding iso

main {

    ubyte rulenumber

    bool[256] cells_previous
    bool[256] cells

    sub start() {
        setup()
        txt.clear_screen()
        init_automaton(rulenumber)

        ubyte y
        for y in 32 to 199+32 {
            txt.print_ub(y)
            txt.chrout(' ')
            for cx16.r0bL in cells {
                txt.chrout('0' + cx16.r0L)
            }
            txt.nl()

            sys.memcopy(cells, cells_previous, sizeof(cells))
            ubyte @zp x
            for x in 0 to len(cells)-1 {
                cells[x] = generate(x)         ; next generation
            }
        }
        sys.poweroff_system()
    }

    sub setup() {
        txt.iso()
        str userinput = "?"*10
        txt.print("\n\nwolfram's cellular automatons.\n\n")
        txt.print("suggestions for interesting rules:\n 30, 45, 90, 110, 117, 184.\n\n")
        txt.print("enter rule number, 0-255: ")
        void txt.input_chars(userinput)
        rulenumber = conv.str2ubyte(userinput)
        txt.print("parsed rule number=")
        txt.print_ub(rulenumber)
        txt.nl()
        for cx16.r0L in 0 to len(cells)-1
            cells[cx16.r0L] = math.rnd() >= 128
    }

    bool[8] states

    sub init_automaton(ubyte number) {
        ubyte state
        for state in 0 to 7 {
            number >>=1
            if_cs
                states[state] = true
            else
                states[state] = false
        }
    }

    sub generate(ubyte x) -> bool {
        ubyte pattern = 0
        if cells_previous[x-1]
            pattern |= %100
        if cells_previous[x]
            pattern |= %010
        if cells_previous[x+1]
            pattern |= %001

        return states[pattern]
    }
}

