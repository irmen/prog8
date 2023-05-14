%import textio
%zeropage basicsafe
%option no_sysinit

; The documentation for a custom key handler can be found here:
; https://github.com/X16Community/x16-docs/blob/master/X16%20Reference%20-%2002%20-%20Editor.md#custom-keyboard-scancode-handler

main {

    sub start() {

        txt.print("custom key handler test - press keys! esc to quit!\n")

        sys.set_irqd()
        uword old_keyhdl = cx16.KEYHDL
        cx16.KEYHDL = &main.key_handler.asm_hook
        sys.clear_irqd()

        bool escape_pressed
        while not escape_pressed {
            ; do nothing here, just wait until ESC is pressed
        }

        sys.set_irqd()
        cx16.KEYHDL = old_keyhdl
        sys.clear_irqd()
    }

    sub key_handler(ubyte keycode) -> bool {
        txt.print_ubhex(keycode, true)
        txt.spc()
        if keycode & $80
            txt.chrout('u')
        else
            txt.chrout('d')
        txt.nl()
        if keycode==$6e {
            main.start.escape_pressed=true
        }
        return false        ; this eats the keypress - return true if you want to pass it through

        asmsub asm_hook(ubyte keycode @A) -> ubyte @A {
            %asm {{
                sta  key_handler.keycode
                pha
                jsr  key_handler
                tay
                pla
                cpy  #0
                beq  +
                rts
+               tya
                rts
            }}
        }
    }

}
