; conway's game of life.

%import math
%import textio

life {
    const ubyte WIDTH = 40
    const ubyte HEIGHT = 30
    const uword STRIDE = $0002+WIDTH
    uword world1 = memory("world1", (WIDTH+2)*(HEIGHT+2), 0)
    uword world2 = memory("world2", (WIDTH+2)*(HEIGHT+2), 0)
    uword @requirezp active_world = world1

   sub benchmark(uword max_time) -> uword {
        txt.clear_screen()
        sys.memset(world1, (WIDTH+2)*(HEIGHT+2), 0)
        sys.memset(world2, (WIDTH+2)*(HEIGHT+2), 0)

        set_start_gen()

        uword gen
        cbm.SETTIM(0,0,0)

        while cbm.RDTIM16()<max_time {
            next_gen()
            gen++
        }

        return gen
    }

    sub set_start_gen() {

; some way to set a custom start generation:
;        str start_gen = "                " +
;                        "                " +
;                        "                " +
;                        "          **    " +
;                        "        *    *  " +
;                        "       *        " +
;                        "       *     *  " +
;                        "       ******   " +
;                        "                " +
;                        "                " +
;                        "                " +
;                        "                " +
;                        "                " +
;                        "                " +
;                        "                " +
;                        "               "
;
;        for y in 0 to 15 {
;            for x in 0 to 15 {
;                if start_gen[y*16 + x]=='*'
;                    active_world[offset + x] = 1
;            }
;            offset += STRIDE
;        }

        ; randomize whole world
        math.rndseed(12345,9999)
        uword offset = STRIDE+1
        ubyte x
        ubyte y
        for y in 0 to HEIGHT-1 {
            for x in 0 to WIDTH-1 {
                active_world[offset+x] = math.rnd() & 1
            }
            offset += STRIDE
        }
    }

    sub next_gen() {
        const ubyte DXOFFSET = 0
        const ubyte DYOFFSET = 0
        ubyte[2] cell_chars = [sc:' ', sc:'●']

        uword @requirezp new_world = world1
        if active_world == world1
            new_world = world2

        ; To avoid re-calculating word index lookups into the new- and active world arrays,
        ; we calculate the required pointer values upfront.
        ; Inside the loop we can use ptr+x just fine (results in efficient LDA (ptr),Y instruction because x is a byte type),
        ; and for each row we simply add the stride to the pointer.
        ; It's more readable to use active_world[offset] etc, but offset is a word value, and this produces
        ; inefficient assembly code because we can't use a register indexed mode in this case. Costly inside a loop.

        uword @requirezp new_world_ptr = new_world + STRIDE+1-DXOFFSET
        uword @requirezp active_world_ptr = active_world + STRIDE+1-DXOFFSET

        ubyte x
        ubyte y
        for y in DYOFFSET to HEIGHT+DYOFFSET-1 {

            cx16.vaddr_autoincr(1, $b000 + 256*y, 0, 2)     ;  allows us to use simple Vera data byte assigns later instead of setchr() calls

            for x in DXOFFSET to WIDTH+DXOFFSET-1 {
                ; count the living neighbors
                ubyte cell = @(active_world_ptr + x)
                uword @requirezp ptr = active_world_ptr + x - STRIDE - 1
                ubyte neighbors = @(ptr) + @(ptr+1) + @(ptr+2) +
                                  @(ptr+STRIDE) + cell + @(ptr+STRIDE+2) +
                                  @(ptr+STRIDE*2) + @(ptr+STRIDE*2+1) + @(ptr+STRIDE*2+2)

                ; apply game of life rules
                if neighbors==3
                    cell=1
                else if neighbors!=4
                    cell=0
                @(new_world_ptr + x) = cell

                ; draw new cell
                ; txt.setchr(x,y,cell_chars[cell])
                cx16.VERA_DATA0 = cell_chars[cell]
            }
            active_world_ptr += STRIDE
            new_world_ptr += STRIDE
        }

        active_world = new_world
    }
}
