%import textio
%import floats

main {
    const ubyte N_ITER = 10
    const ubyte SIZE = 32

    float[SIZE] array = 0.0

    sub testpow(float x, uword y) -> float {
        float tmp = x
        if y==0
            return 1
        repeat y-1 {
            tmp *= x
        }
        return tmp
    }

    sub start() {
        txt.print("calculating...")
        c64.SETTIM(0,0,0)

        float res
        uword i
        ubyte j
        for i in 0 to N_ITER-1 {
            for j in 0 to SIZE-1 {
                array[j] += testpow(2.5/(i+1.0), j)
            }
        }
        for j in 0 to SIZE-1 {
            res += array[j]
        }

        floats.print_f(res)
        txt.nl()
        txt.print_uw(c64.RDTIM16())
        txt.print(" jiffies")
        sys.wait(100)
    }
}
