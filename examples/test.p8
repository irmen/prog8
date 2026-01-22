%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        txt.print_uw(math.gcd(1071, 462))
        txt.spc()
        txt.print_uw(euclidean_gcd(1071, 462))
        txt.nl()
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
