; vertb-demo.p8 - vertical blank interrupt demonstration.
;
; Uses the VERTB IRQ handler to drive a color cycling effect on the
; background. Each vertical blank, the handler increments a frame counter
; and updates COLOR00 (the background color). The main loop checks for
; the left mouse button and exits cleanly on click.
;
; Bare-metal: takes over the system with grab_system().

%import exec
%import custom
%import textio

main {
    uword @shared framecount = 0

    sub vblank_handler() {
        custom.ack_irq(custom.INTF_VERTB)       ; ack the VERTB irq
        framecount++
        custom.COLOR00 = framecount
    }

    sub start() {
        txt.print("VERTB IRQ demo\n")
        txt.print("background color cycles on every vblank\n")
        txt.print("click left mouse button to exit\n")
        sys.wait(120)

        custom.grab_system(true)

        ; Install the VERTB handler (level 3).
        ; Clear any pending VERTB request and enable VERTB.
        custom.set_level3_handler(&vblank_handler)
        custom.ack_irq(custom.INTF_VERTB)
        custom.enable_irq(custom.INTF_VERTB)

        while not custom.left_button() {
            ; wait for left mouse click
        }

        ; Clean shutdown.
        custom.disable_irq(custom.INTF_VERTB)
        custom.restore_system()
        txt.print("\n")
    }
}
