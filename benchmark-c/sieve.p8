%import textio
%import floats


main {
    sub start() {
        txt.lowercase()
        test.benchmark_name()
        cbm.SETTIM(0,0,0)
        test.benchmark()
        txt.print_f(floats.time() / 60)
        txt.print(" seconds\n")
        void test.benchmark_check()
        repeat {}
    }    
}


test {
    const ubyte N_ITER = 10
    const uword SIZE = 8191
    const uword EXPECTED = 1900
    uword prime_count
    ^^bool @zp flags = memory("flags", SIZE, 0)

    sub benchmark_name()
    {
        txt.print("sieve.c\n")
        txt.print("Calculates the primes from 1 to ")
        txt.print_uw(SIZE * 2 + 2)
        txt.print(" (")
        txt.print_ub(N_ITER)
        txt.print(" iterations)\n")
    }

    sub benchmark()
    {
        repeat N_ITER
            prime_count = sieve(SIZE)
    }
    
    sub benchmark_check() -> bool
    {
        txt.print("count=")
        txt.print_uw(prime_count)

        if prime_count == EXPECTED
        {
            txt.print(" [OK]")
            return false
        }

        txt.print(" [FAIL] - expected ")
        txt.print_uw(EXPECTED)
        return true
    }

    
    sub sieve(uword size) -> uword 
    {
        uword i, prime, k
        uword count = 1

        for i in 0 to size-1
            flags[i] = true

        for i in 0 to size-1 
        {
            if flags[i]
            {
                prime = i + i + 3
                k = i + prime
                while k < size
                {
                    flags[k] = false
                    k += prime
                }
                count++
            }
        }
    
        return count
    }

}
