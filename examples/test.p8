%import textio
%import floats
%import math
%zeropage basicsafe

main {
    sub start() {
        byte[3] bytes = [9,-11,-22]
        word[3] words = [9,-1111,-2222]
        word[3] @nosplit words2 = [9,-1111,-2222]
        long[3] longs = [9,-1111111,-2222222]
        float[3] floata = [9,-1.111,-2.222]

        cx16.r9L = 3
        cx16.r10L = 3

        swap(bytes[cx16.r9L-1], bytes[cx16.r10L-2])
        swap(words[cx16.r9L-1], words[cx16.r10L-2])
        swap(words2[cx16.r9L-1], words2[cx16.r10L-2])
        swap(longs[cx16.r9L-1],longs[cx16.r10L-2])
        swap(floata[cx16.r9L-1],floata[cx16.r10L-2])

        txt.print_b(bytes[1])
        txt.spc()
        txt.print_b(bytes[2])
        txt.nl()

        txt.print_w(words[1])
        txt.spc()
        txt.print_w(words[2])
        txt.nl()

        txt.print_w(words2[1])
        txt.spc()
        txt.print_w(words2[2])
        txt.nl()

        txt.print_l(longs[1])
        txt.spc()
        txt.print_l(longs[2])
        txt.nl()

        txt.print_f(floata[1])
        txt.spc()
        txt.print_f(floata[2])
        txt.nl()
    }
}
