%import c64utils
%zeropage basicsafe

~ main {

    ubyte[256] sieve
    ubyte candidate_prime = 2       ; is increased in the loop

    sub start() {
        memset(sieve, 256, false)   ; clear the sieve, to reset starting situation on subsequent runs

        ; calculate primes
        c64scr.print("prime numbers up to 255:\n\n")
        ubyte amount=0
        while true {
            ubyte prime = find_next_prime()
            if prime==0
                break
            c64scr.print_ub(prime)
            c64scr.print(", ")
            amount++
        }
        c64.CHROUT('\n')
        c64scr.print("number of primes (expected 54): ")
        c64scr.print_ub(amount)
        c64.CHROUT('\n')
    }


    sub find_next_prime() -> ubyte {

        while sieve[candidate_prime] {
            candidate_prime++
            if candidate_prime==0
                return 0        ; we wrapped; no more primes available in the sieve
        }

        ; found next one, mark the multiples and return it.
        sieve[candidate_prime] = true
        uword multiple = candidate_prime


        while multiple < len(sieve) {
            sieve[lsb(multiple)] = true
            multiple += candidate_prime
            c64scr.print_uw(multiple)       ; TODO
            c64.CHROUT('\n')        ; TODO
        }
        return candidate_prime
    }
}
