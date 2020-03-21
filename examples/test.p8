%import c64utils
;%import c64flt
;%option enable_floats
%zeropage basicsafe

main {

    sub start() {
        str input="???????"

        ubyte guess = lsb(c64utils.str2uword(input))
    }
}
