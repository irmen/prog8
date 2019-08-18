%import c64lib
%import c64utils
%zeropage basicsafe

main {

    sub start() {

        const ubyte i = 33

        i=33
        i++
        const ubyte q=33
        for q in [1,3,5,99] {
            A=i
        }

        while q<10 {
            q++
        }

    }
}
