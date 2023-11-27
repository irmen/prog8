%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        float[51] p1
        float[51] p2
        float[51] p3
        float[51] p4

        ubyte idx = 2
        float fl = 3.455
        p1[idx+1] = fl
        floats.print_f(p1[idx+1])
        p1[idx+1] = 0.0
        floats.print_f(p1[idx+1])

        store_prime(1, 2.987654321)
        store_prime(52, 3.14159)

        floats.print_f(p1[1])
        txt.nl()
        floats.print_f(p2[2])
        txt.nl()


        sub store_prime(ubyte idx, float pr) {
            if idx >= 150 {
                p4[idx - 150] = pr
            } else if idx >= 100 {
                p3[idx - 100] = pr
            } else if idx >= 50 {
                p2[idx - 50] = pr
            } else {
                p1[idx] = pr
            }
        }
    }
}
