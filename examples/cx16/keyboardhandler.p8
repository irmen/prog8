%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {

        txt.print("ps2 custom key handler test - press keys! esc to quit!\n")

        sys.set_irqd()
        uword old_keyhdl = cx16.KEYHDL
        cx16.KEYHDL = &main.keyboard_scancode_handler.asm_shim
        sys.clear_irqd()

        bool escape_pressed
        while not escape_pressed {
            ; just sit here
        }
        sys.set_irqd()
        cx16.KEYHDL = old_keyhdl
        sys.clear_irqd()
    }

    sub keyboard_scancode_handler(ubyte prefix, ubyte scancode, bool updown) -> bool {
        txt.print_ubhex(prefix, true)
        txt.chrout(':')
        txt.print_ubhex(scancode, true)
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
        return true     ; true = consume key event, false = continue processing it

asm_shim:
        %asm {{
            php
            pha
            phx
            stz  updown
            bcc  +
            inc  updown
+           stx  prefix
            sta  scancode
            jsr  keyboard_scancode_handler
            beq  +
            plx
            pla
            lda #0     ;By setting A=0 we will consume this key event
            tax
            plp
            rts
+           plx
            pla     ; leave A untouched, continue processing
            plp
            rts
        }}
    }
}
