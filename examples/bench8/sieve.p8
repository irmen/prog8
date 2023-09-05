%import textio
%import floats

; The "Byte Sieve" test.  https://en.wikipedia.org/wiki/Byte_Sieve
; Note: this program can be compiled for multiple target systems.

main {
    sub start() {


        const ubyte ITERS = 10
        uword count
        uword i
        uword prime
        uword k
        const uword SIZEPL = 8191
        uword @zp flags_ptr = memory("flags", SIZEPL, $100)

        txt.print_ub(ITERS)
        txt.print(" iterations, calculating...\n")

        cbm.SETTIM(0, 0, 0)

        repeat ITERS {
            sys.memset(flags_ptr, SIZEPL, 1)
            count = 0
            for i in 0 to SIZEPL-1 {
                if flags_ptr[i] {
                    prime = i*2 + 3
                    k = i + prime
                    while k < SIZEPL {
                        flags_ptr[k] = false
                        k += prime
                    }
;                    txt.print_uw(prime)
;                    txt.spc()
                    count++
                }
            }
        }

        txt.print_uw(count)
        txt.print(" primes\n")

        float time = cbm.RDTIM16() as float / 60.0
        floats.print_f(time)
        txt.print(" sec total = ")
        floats.print_f(time/ITERS)
        txt.print(" sec per iteration\n")
        sys.wait(9999)
    }
}
