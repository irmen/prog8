%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
        delay1(1)
        delay2(1)
        delay3(1)

        sub delay1(ubyte note1) {
            A= note1
        }
        sub delay2(ubyte note1) {
            A= note1
        }
        sub delay3(ubyte note1) {
            A= note1
        }

    }

}
