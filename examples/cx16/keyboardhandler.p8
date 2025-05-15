%import textio
%zeropage basicsafe
%option no_sysinit

; The documentation for custom key handlers can be found here:
; https://github.com/X16Community/x16-docs/blob/master/X16%20Reference%20-%2003%20-%20Editor.md#custom-keyboard-keynum-code-handler

main {

    bool stop_program

    sub start() {
        stop_program = false
        txt.print("custom key handler test - press keys! esc to quit!\n")

        sys.set_irqd()
        uword old_keyhdl = cx16.KEYHDL
        cx16.KEYHDL = &keyboard_handler
        sys.clear_irqd()

        while not stop_program {
            ; wait
        }

        sys.set_irqd()
        cx16.KEYHDL = old_keyhdl
        sys.clear_irqd()
    }

    sub keyboard_handler(ubyte keynum) -> ubyte {
        ; NOTE: this handler routine expects the keynum in A and return value in A
        ;       which is thankfully how prog8 translates this subroutine's calling convention.
        ; NOTE: it may be better to store the keynum somewhere else and let the main program
        ;       loop figure out what to do with it, rather than putting it all in the handler routine

        txt.print_ubhex(keynum, true)
        txt.spc()
        if keynum & $80 !=0
            txt.chrout('u')
        else
            txt.chrout('d')
        txt.nl()

        if keynum==$6e {
            ; escape stops the program
            main.stop_program = true
        }
        return 0        ; By returning 0 (in A) we will eat this key event. Return the original keynum value to pass it through.
    }
}
