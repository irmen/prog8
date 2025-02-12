%import textio
%zeropage basicsafe
%launcher none
%output xex

; This example computes the first 20 values of the Fibonacci sequence.
; Note: this program is compatible with atari.

main {
    sub start() {
        txt.print("fibonacci sequence")
        txt.nl()

        repeat 21 {
            txt.print_uw(fib_next())
            txt.nl()
        }

        void txt.waitkey()
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
