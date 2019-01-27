%import c64utils

~ main {


    sub start() {
        ; clear the sieve, and mark 0 and 1 as not prime.
        memset(sieve, 256, false)
        sieve[0] = true
        sieve[1] = true

        ; calculate primes
        c64scr.print("prime numbers up to 255:\n\n")
        while true {
            ubyte prime = find_next_prime()
            if prime==0
                break
            c64scr.print_ub(prime)
            c64scr.print(", ")
        }
        c64.CHROUT('\n')
    }

    ubyte[256] sieve

    sub find_next_prime() -> ubyte {
        for ubyte prime in 2 to 255 {
            if not sieve[prime] {
                ; found one, mark the multiples and return it.
                sieve[prime] = true
                uword multiple = prime**2
                while multiple < len(sieve) {
                    sieve[lsb(multiple)] = true
                    multiple += prime
                }
                return prime
            }
        }
        return 0
    }
}
