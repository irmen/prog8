%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        uword[] test_cases_a = [48, 56, 17, 1071, 100, 17, 0, 100, 256, 123]
        uword[] test_cases_b = [18, 42, 13, 462, 25, 0, 23, 75, 64, 456]

        ubyte index
        for index in 0 to len(test_cases_a)-1 {
            uword r1 = math.gcd(test_cases_a[index], test_cases_b[index])
            uword r2 = euclidean_gcd(test_cases_a[index], test_cases_b[index])
            txt.print_uw(r1)
            txt.spc()
            txt.print_uw(r2)
            if r1 != r2
                txt.print("  fail\n")
            else
                txt.print("  ok\n")
        }
    }

    sub euclidean_gcd(uword a, uword b) -> uword {
        while b != 0 {
            uword temp = b
            b = a % b
            a = temp
        }
        return a
    }
}
