%import textio
%import gfx_lores
%import emudbg
%zeropage basicsafe
%option no_sysinit

main {
    const uword WIDTH = 320
    const ubyte HEIGHT = 240

    sub start() {
        uword clo, chi

        void cx16.set_screen_mode(128)

        word x1, y1, x2, y2
        ubyte i
        ubyte color = 2

        sys.set_irqd()
        emudbg.reset_cpu_cycles()
        for i in 0 to 254 step 4 {
            x1 = ((WIDTH-256)/2 as word) + math.sin8u(i) as word
            y1 = (HEIGHT-128)/2 + math.cos8u(i)/2
            x2 = ((WIDTH-64)/2 as word) + math.sin8u(i)/4 as word
            y2 = (HEIGHT-64)/2 + math.cos8u(i)/4
            cx16.GRAPH_set_colors(color, 0, 1)
            cx16.GRAPH_draw_line(x1 as uword, y1 as uword, x2 as uword, y2 as uword)
        }
        clo, chi = emudbg.cpu_cycles()
        sys.clear_irqd()

        txt.print_uwhex(chi, true)
        txt.print_uwhex(clo, false)
        txt.nl()

        sys.wait(50)
        cx16.GRAPH_clear()
        sys.wait(50)

        sys.set_irqd()
        emudbg.reset_cpu_cycles()
        color = 5
        for i in 0 to 254 step 4 {
            x1 = ((WIDTH-256)/2 as word) + math.sin8u(i) as word
            y1 = (HEIGHT-128)/2 + math.cos8u(i)/2
            x2 = ((WIDTH-64)/2 as word) + math.sin8u(i)/4 as word
            y2 = (HEIGHT-64)/2 + math.cos8u(i)/4
            gfx_lores.line(x1 as uword, y1 as ubyte, x2 as uword, y2 as ubyte, color)
        }
        clo, chi = emudbg.cpu_cycles()
        sys.clear_irqd()

        txt.print_uwhex(chi, true)
        txt.print_uwhex(clo, false)
        txt.nl()


    }
}

