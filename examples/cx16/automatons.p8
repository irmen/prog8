; Wolfram's Cellular Automatons.

%import math
%import conv
%import textio
%option no_sysinit
%zeropage basicsafe

main {

    ubyte rulenumber

    bool[256] cells_previous
    bool[256] cells

    sub start() {
        cx16.set_screen_mode(128)
        setup()
        txt.clear_screen()
        print_title(rulenumber)

        init_automaton(rulenumber)

        ubyte y
        for y in 32 to 199+32 {
            cx16.FB_cursor_position((320-len(cells))/2,y)
            cx16.FB_set_pixels(cells, len(cells))
            sys.memcopy(cells, cells_previous, sizeof(cells))
            ubyte @zp x
            for x in 0 to len(cells)-1 {
                cells[x] = generate(x)         ; next generation
            }
        }
    }

    sub setup() {
        str userinput = "?"*10
        txt.print("\n\nwolfram's cellular automatons.\n\n")
        txt.print("suggestions for interesting rules:\n 30, 45, 90, 110, 117, 184.\n\n")
        txt.print("enter rule number, 0-255: ")
        void txt.input_chars(userinput)
        rulenumber = conv.str2ubyte(userinput)
        txt.print("\nstart state: (r)andomize or (s)ingle? ")
        void txt.input_chars(userinput)
        if userinput[0]=='r' {
            for cx16.r0L in 0 to len(cells)-1
                cells[cx16.r0L] = math.rnd() >= 128
        } else {
            cells[len(cells)/2] = true
        }
    }

    sub print_title(ubyte number) {
        cx16.FB_cursor_position(92,16)
        for cx16.r9L in "Cellular Automaton #" {
            cx16.GRAPH_put_next_char(cx16.r9L)
        }
        ^^ubyte num_str = conv.str_ub(number)
        do {
            cx16.GRAPH_put_next_char(@(num_str))
            num_str++
        } until @(num_str)==0
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

