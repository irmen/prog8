%target cx16
%import gfx2
%import textio
%zeropage basicsafe

main {

    sub start () {
        gfx2.text_charset(3)

        test()

        gfx2.screen_mode(0)
        txt.print("done!\n")
    }

    sub test() {
        gfx2.screen_mode(6)

        ubyte color
        uword yy
        for color in 3 downto 0 {
            for yy in 100 to 120 {
                uword xx
                for xx in 10 to 500 {
                    gfx2.plot(xx, yy, color)
                }
            }
        }


        for color in 3 downto 0 {
            for yy in 130 to 150 {
                gfx2.horizontal_line(10, yy, 400, color)
            }
        }

        sys.wait(5*60)
    }
}
