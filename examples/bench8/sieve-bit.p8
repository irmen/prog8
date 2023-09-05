%import textio
%import floats

main {
    const ubyte N_ITER = 4
    const uword SIZE = 16000

    uword @zp flags_ptr = memory("flags", SIZE/8+1, $100)
    ubyte[] bitv = [ $01, $02, $04, $08, $10, $20, $40, $80 ]

    sub start() {
        txt.print_ub(N_ITER)
        txt.print(" iterations, calculating... (expecting 3431)\n")

        cbm.SETTIM(0, 0, 0)
        uword prime_count
        repeat N_ITER {
            prime_count = sieve()
        }

        txt.print_uw(prime_count)
        txt.print(" primes\n")

        float time = cbm.RDTIM16() as float / 60.0
        floats.print_f(time)
        txt.print(" sec total = ")
        floats.print_f(time/N_ITER)
        txt.print(" sec per iteration\n")
        sys.wait(9999)
    }

    sub check_flag(uword idx) -> ubyte
    {
        return flags_ptr[idx/8] & bitv[lsb(idx)&7]
    }

    sub clear_flag(uword idx)
    {
        flags_ptr[idx/8] &= ~bitv[lsb(idx)&7]
    }

    sub sieve() -> uword {
        uword prime
        uword k
        uword count=0
        uword i
        sys.memset(flags_ptr, SIZE/8+1, $ff)

        for i in 0 to SIZE-1 {
            if check_flag(i) {
                prime = i*2 + 3
                k = i + prime
                while k < SIZE {
                    clear_flag(k)
                    k += prime
                }
;                    txt.print_uw(prime)
;                    txt.spc()
                count++
            }
        }
        return count
    }
}
