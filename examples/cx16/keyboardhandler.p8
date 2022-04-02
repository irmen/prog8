%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {

        txt.print("ps2 scancode handler test - press keys! esc to quit!\n")

        sys.set_irqd()
        uword old_keyhdl = cx16.KEYHDL
        cx16.KEYHDL = &main.keyboard_scancode_handler.asm_shim
        sys.clear_irqd()

        ubyte escape_pressed
        while not escape_pressed {
            ; just sit here
        }
        sys.set_irqd()
        cx16.KEYHDL = old_keyhdl
        sys.clear_irqd()
    }

    sub keyboard_scancode_handler(ubyte prefix, ubyte scancode, ubyte updown) {
        txt.print_ub(prefix)
        txt.spc()
        txt.print_ub(scancode)
        txt.spc()
        if updown
            txt.chrout('u')
        else
            txt.chrout('d')
        txt.nl()
        if prefix==0 and scancode==119 and updown {
            ; escape was pressed! exit back to basic
            main.start.escape_pressed = true
        }
        return

asm_shim:
        %asm {{
            php
            pha
            phx
            sta  scancode
            stz  updown
            bcc  +
            inc  updown
+           jsr  keyboard_scancode_handler
            plx
            pla
            plp
            rts
        }}
    }
}
