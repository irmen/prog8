%import textio
%zeropage basicsafe
%option no_sysinit

; The documentation for custom PS2 key handlers can be found here:
; https://github.com/X16Community/x16-docs/blob/master/X16%20Reference%20-%2002%20-%20Editor.md#custom-keyboard-scancode-handler

main {

    sub start() {

        txt.print("custom key handler test - press keys! esc to quit!\n")

        sys.set_irqd()
        uword old_keyhdl = cx16.KEYHDL
        cx16.KEYHDL = &keyboard_scancode_handler
        sys.clear_irqd()

        while handle_keyboard_event() {
        }

        sys.set_irqd()
        cx16.KEYHDL = old_keyhdl
        sys.clear_irqd()
    }

    ; Keyboard handler communication variables.
    ; these need to be in block scope instead of in a subroutine,
    ; so that they won't get overwritten with initialization values every time.
    ; The assembly keyboard handler will set these, prog8 will read them.
    bool @shared keyhdl_event       ; is there a keyboard event to handle?
    ubyte @shared keyhdl_scancode

    sub handle_keyboard_event() -> bool {
        ; Potentially handle keyboard event.
        ; Note that we do this from the program's main loop instead of
        ; the actual keyboard handler routine itself.
        ; The reason for this is documented below in the handler assembly routine.
        if not keyhdl_event
            return true
        keyhdl_event = false
        txt.print_ubhex(keyhdl_scancode, true)
        txt.spc()
        if keyhdl_scancode & $80
            txt.chrout('u')
        else
            txt.chrout('d')
        txt.nl()
        return keyhdl_scancode!=$6e         ; escape breaks the loop
    }

    asmsub keyboard_scancode_handler() {
        ; NOTE that the keyboard handler is an asm subroutine.
        ; Unfortunately is it not possible to use prog8 code or calls here,
        ; because the X register gets overwritten here by the kernal.
        ; Pog8 uses the X register internally (for the software eval stack).
        ; So it is unsafe to call prog8 code from here because the evaluation stack pointer
        ; will be invalid which produces undefined results.
        ; So, instead, we store the various keyboard event bytes and signal
        ; the main prog8 program that a keyboard event has occurred.
        ; It then processes it independently from the assembly code here.
        ;
        ; Unfortunately this also means you cannot decide easily from that prog8 code
        ; if the keyboard press should be consumed/ignored or put into the keyboard queue
        ; (this is controlled by returning 0 or 1 in register A here)

        %asm {{
            pha
            sta  keyhdl_scancode
            lda  #1
            sta  keyhdl_event
            pla

            lda #0     ; By setting A=0 we will eat this key event. leave A unchanged to pass it through.
            rts
        }}
    }
}
