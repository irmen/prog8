%import conv
%option no_sysinit

main {
    sub start() {
        read4hex()
    }

    sub read4hex() -> uword {
        str hex = "0000"
        hex[0] = cbm.CHRIN()
        hex[1] = cbm.CHRIN()
        hex[2] = cbm.CHRIN()
        hex[3] = cbm.CHRIN()
        return conv.hex2uword(hex)
    }

}
