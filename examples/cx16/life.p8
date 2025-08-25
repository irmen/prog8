; Conway's game of life.
; the world is represented by a matrix of bytes (the cells) that can be 1 (alive) or 0 (dead)
; numeric byte is used because we need to count the number of neighbors and that would require casts if we used booleans.
; (but you totally could use booleans)

%import math
%import textio

main {
    const ubyte WIDTH = 80
    const ubyte HEIGHT = 60-4
    const uword STRIDE = $0002+WIDTH
    ^^ubyte world1 = memory("world1", (WIDTH+2)*(HEIGHT+2), 0)
    ^^ubyte world2 = memory("world2", (WIDTH+2)*(HEIGHT+2), 0)
    ^^ubyte @requirezp active_world = world1

   sub start() {
        ; cx16.set_screen_mode(3)
        txt.cls()
        txt.color(8)
        txt.plot(50,0)
        txt.print("prog8 - conway's game of life")
        sys.memset(world1, (WIDTH+2)*(HEIGHT+2), 0)
        sys.memset(world2, (WIDTH+2)*(HEIGHT+2), 0)

        set_start_gen()

        ubyte gen_add
        uword gen
        repeat {
            if gen_add==0
                cbm.SETTIM(0,0,0)

            next_gen()

            gen++
            txt.home()
            txt.color(5)
            txt.print(" gen ")
            txt.print_uw(gen)

            gen_add++
            if gen_add==10 {
                txt.print("  jiffies/10 gens: ")
                txt.print_uw(cbm.RDTIM16())
                txt.print("  ")
                gen_add=0
            }
        }
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
        const ubyte DYOFFSET = 2
        ubyte[2] cell_chars = [sc:' ', sc:'●']

        ^^ubyte @requirezp new_world = world1
        if active_world == world1
            new_world = world2

        ; To avoid re-calculating word index lookups into the new- and active world arrays,
        ; we calculate the required pointer values upfront.
        ; Inside the loop we can use ptr+x just fine (results in efficient LDA (ptr),Y instruction because x is a byte type),
        ; and for each row we simply add the stride to the pointer.
        ; It's more readable to use active_world[offset] etc, but offset is a word value, and this produces
        ; inefficient assembly code because we can't use a register indexed mode in this case. Costly inside a loop.

        ^^ubyte @requirezp new_world_ptr = new_world + STRIDE+1-DXOFFSET
        ^^ubyte @requirezp active_world_ptr = active_world + STRIDE+1-DXOFFSET

        ubyte x
        ubyte y
        for y in DYOFFSET to HEIGHT+DYOFFSET-1 {

            cx16.vaddr_autoincr(1, $b000 + 256*y, 0, 2)     ;  allows us to use simple Vera data byte assigns later instead of setchr() calls

            for x in DXOFFSET to WIDTH+DXOFFSET-1 {
                ; count the living neighbors
                ubyte cell = @(active_world_ptr + x)
                ^^ubyte @requirezp ptr = active_world_ptr + x - STRIDE - 1
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
