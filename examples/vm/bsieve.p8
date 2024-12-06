
; The "Byte Sieve" test.  https://en.wikipedia.org/wiki/Byte_Sieve

%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        uword count
        uword i
        uword prime
        uword k
        const uword SIZEPL = 8191
        uword @zp flags_ptr = memory("flags", SIZEPL, $100)

        txt.print("calculating...\n")

        sys.memset(flags_ptr, SIZEPL, 1)
        count = 0
        for i in 0 to SIZEPL-1 {
            if flags_ptr[i]!=0 {
                prime = i + i + 3
                k = i + prime
                while k <= SIZEPL-1 {
                    flags_ptr[k] = 0    ; false
                    k += prime
                }
                ; txt.print_uw(prime)
                ; txt.nl()
                count++
            }
        }

        txt.nl()
        txt.print_uw(count)
        txt.print(" primes\n")
    }
}
