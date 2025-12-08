%import textio
%import floats
%import ciatimer


main {
    sub start() {
        txt.lowercase()
        cia.calibrate()
        test.benchmark_name()
        test.benchmark()
        void test.benchmark_check()
        cia.print_time()
        repeat {}
    }    
}

test {

    const ubyte N_ITER = 10
    const ubyte SIZE = 32
    float[SIZE] array

    const float expected = 3614007361536.000000
    const float epsilon = 10000000
    float res

    sub benchmark_name()
    {
        txt.print("pow.p8\n")
        txt.print("Calculates floating point exponential (")
        txt.print_uw(N_ITER)
        txt.print(" iterations)\n")
    }

    sub benchmark()
    {
        ubyte i,j
        res = 0
        
        for j in 0 to SIZE-1 {
            array[j]=0
        }

        for i in 0 to N_ITER-1 {
            for j in 0 to SIZE-1 {
                array[j] += testpow(2.5 / ((i + 1) as float), j)
            }
        }
        
        for j in 0 to SIZE-1 {
            res += array[j]
        }
    }

    sub testpow(float x, ubyte y) -> float
    {
        float tmp = x
    
        if y == 0
            return 1
    
        repeat y-1
            tmp *= x
    
        return tmp
    }


    sub benchmark_check() -> bool
    {
        txt.print("res      = ")
        txt.print_f(res)
        float diff = expected - res;
        txt.print("\nexpected = ")
        txt.print_f(expected)
        txt.print("\nepsilon  = ")
        txt.print_f(epsilon)
        txt.print("\ndiff     = ")
        txt.print_f(diff)

        if (diff < epsilon and diff > -epsilon)
        {
            txt.print(" [OK]\n")
            return false
        }

        txt.print("[FAIL]\n")
        return true
    }
    

}
