bcd {
    ; Decimal addition an subtraction routines.
    ; For CPUs that support BCD mode (binary coded decimal) (not all 6502 variants support this mode...)
    ; This is useful for example for counting decimal score in a game, to avoid costly conversion to a decimal display string. Just print the hex representation.

    sub addb(byte a, byte b) -> byte {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub addub(ubyte a, ubyte b) -> ubyte {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub addw(word a, word b) -> word {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub adduw(uword a, uword b) -> uword {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub addl(long a, long b) -> long {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub subb(byte a, byte b) -> byte {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    sub subub(ubyte a, ubyte b) -> ubyte {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    sub subuw(uword a, uword b) -> uword {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    sub subl(long a, long b) -> long {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    inline asmsub setbcd() {
        %asm {{
            php
            sei
            sed
        }}
    }

    inline asmsub clearbcd() {
        %asm {{
            plp
        }}
    }
}
