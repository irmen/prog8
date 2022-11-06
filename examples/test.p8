%import gfx2
%import textio
%zeropage basicsafe

main {
    sub start() {
        gfx2.screen_mode(6)
        ubyte pix1 = gfx2.pget(162,120)
        gfx2.plot(162,120,7)
        ubyte pix2 = gfx2.pget(162,120)
        gfx2.plot(162,120,231)
        ubyte pix3 = gfx2.pget(162,120)
        ubyte pix4 = gfx2.pget(163,120)
        ubyte pix5 = gfx2.pget(162,121)
        sys.wait(20)
        gfx2.screen_mode(0)
        txt.print_ub(pix1)
        txt.spc()
        txt.print_ub(pix2)
        txt.spc()
        txt.print_ub(pix3)
        txt.spc()
        txt.print_ub(pix4)
        txt.spc()
        txt.print_ub(pix5)
        txt.nl()
    }
}
