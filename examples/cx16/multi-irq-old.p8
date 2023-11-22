%import palette
%import textio
%import syslib
%zeropage basicsafe

; Example that shows a way to handle multiple IRQ sources on the X16.
; This uses the "OLD" way using the interrupt handler routines in sys.
; (see multi-irq-new example to do it the "new" way)
; Currently only Vera interrupts are supported.   VIA irqs are an exercise for the reader.

main {
    sub start() {
        sys.set_rasterline(150)
        sys.set_irq(&irq.master_handler)        ; ..will just enable vsync..
        cx16.VERA_IEN |= 2                      ; .. so also enable line irq here.

        txt.print("\n\n\nx16 irq handlers installed (old style)\n")
        txt.print("red = vsync irq\n")
        txt.print("green = first line irq\n")
        txt.print("blue = second line irq\n")
    }
}

irq {
    sub master_handler() -> bool {
        ubyte irqsrc = cx16.VERA_ISR & cx16.VERA_IEN        ; only consider sources that are enabled
        ror(irqsrc)
        if_cs {
            vsync_irq()
            return true     ; run system IRQ handler. It will ack the vsync IRQ as well.
        }
        ror(irqsrc)
        if_cs {
            line_irq()
            cx16.VERA_ISR |= 2  ; ack the irq
            return false
        }
        ror(irqsrc)
        if_cs {
            sprcol_irq()
            cx16.VERA_ISR |= 4  ; ack the irq
            return false
        }
        ror(irqsrc)
        if_cs {
            aflow_irq()
            ; note: AFLOW can only be cleared by filling the audio FIFO for at least 1/4. Not via the ISR bit.
            return false
        }

        ; weird irq
        return false
    }

    sub vsync_irq() {
        cx16.save_vera_context()
        palette.set_color(0, $f00)
        repeat 1000 {
            cx16.r0++
        }
        palette.set_color(0, $000)
        cx16.restore_vera_context()
    }

    sub line_irq() {
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
    }

    sub sprcol_irq() {
        ; nothing here yet
    }

    sub aflow_irq() {
        ; nothing here yet
    }
}
