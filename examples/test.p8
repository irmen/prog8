%import textio
%import test_stack
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    ubyte[256] sieve
    ubyte candidate_prime = 2       ; is increased in the loop

    sub start() {
        sys.memset(sieve, 256, false)   ; clear the sieve, to reset starting situation on subsequent runs

        ; calculate primes
        txt.print("prime numbers up to 255:\n\n")
        ubyte amount=0
        repeat {
            ubyte prime = find_next_prime()
            if prime==0
                break
            txt.print_ub(prime)
            txt.print(", ")
            amount++
        }
        txt.nl()
        txt.print("number of primes (expected 54): ")
        txt.print_ub(amount)
        txt.nl()

        ; test_stack.test()
    }

    sub derp() -> ubyte, ubyte {
        return 0, 1
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
        }
        return candidate_prime
    }
}
