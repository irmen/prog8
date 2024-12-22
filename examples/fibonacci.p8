%import textio
%zeropage basicsafe

; This example computes the first 20 values of the Fibonacci sequence.
; Note: this program can be compiled for multiple target systems.

; Note that the fibonacci subroutine keeps is state in two outer variables
; so that every call to it is able to produce the next single number in the sequence.

main {
    sub start() {
        txt.print("fibonacci sequence\n")

        repeat 21 {
            txt.print_uw(fib_next())
            txt.nl()
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
