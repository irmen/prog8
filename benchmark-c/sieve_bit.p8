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
    const ubyte N_ITER = 4
    const uword SIZE = 16000
    const uword EXPECTED = 3432
    uword prime_count
    ^^ubyte @zp flags = memory("flags", SIZE/8+1, 0)

    sub benchmark_name()
    {
        txt.print("sieve_bit.p8\n")
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

    ubyte[] bitv = [
        $01,    
        $02,
        $04,
        $08,
        $10,
        $20,
        $40,
        $80
    ]

    sub check_flag(uword idx) -> bool
    {
        return flags[idx / 8] & bitv[lsb(idx) & 7] != 0
    }

    sub clear_flag(uword idx)
    {
        flags[idx / 8] &= ~(bitv[lsb(idx) & 7])
    }

    
    sub sieve(uword size) -> uword 
    {
        uword i, prime, k
        uword count=1
    
        for i in 0 to (size / 8)
            flags[i] = $ff

        for i in 0 to size-1
        {
            if check_flag(i)
            {
                prime = i + i + 3
                k = i + prime;
                while k < size
                {
                    clear_flag(k)
                    k += prime
                }
                count++
            }
        }

   
        return count
    }

}
