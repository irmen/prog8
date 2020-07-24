%import c64utils
%zeropage basicsafe

; This example computes the first 20 values of the Fibonacci sequence.

main {
    sub start() {
        c64scr.print("fibonacci sequence\n")

        ; TODO fix the double i=0 assignment generation in the asm code:

        ubyte i
        for i in 0 to 20 {
            c64scr.print_uw(fib_next())
            c64.CHROUT('\n')
        }
    }

    uword fib_prev = 0
    uword fib_current = 1

    sub fib_next() -> uword {
        uword new = fib_current + fib_prev
        fib_prev = fib_current
        fib_current = new
        return fib_prev
    }
}
