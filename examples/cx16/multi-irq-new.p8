%import palette
%import textio
%import syslib
%zeropage basicsafe

; Example that shows a way to handle multiple IRQ sources on the X16.
; This uses the "NEW" way using the X16 specific interrupt handler routines in cx16.
; Currently only Vera interrupts are supported.   VIA irqs are an exercise for the reader.

main {
    sub start() {
        cx16.enable_irq_handlers(true)
        cx16.set_line_irq_handler(150, &irq.line_irq)
        cx16.set_vsync_irq_handler(&irq.vsync_irq)

        txt.print("\n\n\nx16 irq handlers installed (new style)\n")
        txt.print("red = vsync irq\n")
        txt.print("green = first line irq\n")
        txt.print("blue = second line irq\n")
    }
}

irq {
    sub vsync_irq() -> bool {
        cx16.save_vera_context()
        palette.set_color(0, $f00)
        repeat 1000 {
            cx16.r0++
        }
        palette.set_color(0, $000)
        cx16.restore_vera_context()
        return true
    }

    sub line_irq() -> bool {
        cx16.save_vera_context()
        if cx16.VERA_SCANLINE_L==150 {
            palette.set_color(0, $0f0)
            sys.set_rasterline(200)     ; prepare next line irq
        } else {
            palette.set_color(0, $00f)
            sys.set_rasterline(150)     ; back to first line irq
        }
        repeat 500 {
            cx16.r0++
        }
        palette.set_color(0, $000)
        cx16.restore_vera_context()
        return false
    }
}
