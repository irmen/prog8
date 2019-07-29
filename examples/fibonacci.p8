%import c64utils
%zeropage basicsafe

; This example computes the first 20 values of the Fibonacci sequence.
; It uses the feature that variables that don't have an initialization value,
; will retain their previous value over multiple invocations of the program or subroutine.
; This is extremely handy for the Fibonacci sequence because it is defined
; in terms of 'the next value is the sum of the previous two values'

main {
    sub start() {
        c64scr.print("fibonacci sequence\n")
        fib_setup()
        for ubyte i in 0 to 20 {
            c64scr.print_uw(fib_next())
            c64.CHROUT('\n')
        }
    }

    sub fib_setup() {
        ; (re)start the sequence
        main.fib_next.prev=0
        main.fib_next.current=1
    }

    sub fib_next() -> uword {
        uword prev              ; no init value so will retain previous value
        uword current           ; no init value so will retain previous value
        uword new = current + prev
        prev = current
        current = new
        return prev
    }
}
