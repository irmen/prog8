; Internal library routines - always included by the compiler

prog8_lib {
    %option no_symbol_prefixing, ignore_unused

	%asminclude "library:prog8_lib.asm"
	%asminclude "library:prog8_funcs.asm"


    sub sqrt_long(long @nozp num) -> uword {
        ;    Calculate the square root of a 4-byte long value using bitwise operations.
        ; NOTE: this is not in prog8_math because all the other sqrt routines are in prog8_funcs.asm here too

        if num <= 0
            return 0

        alias bit_pos = cx16.r0r1sl
        alias temp = cx16.r2r3sl
        alias numzp = cx16.r4r5sl
        alias res = cx16.r6r7sl

        pushl(cx16.r0r1sl)
        pushl(cx16.r2r3sl)
        pushl(cx16.r4r5sl)
        pushl(cx16.r6r7sl)

        ;    Start with the most significant pair of bits
        ;    For the digit-by-digit algorithm, we process the numzpber in groups of 2 bits
        numzp = num
        res = 0
        bit_pos = 1 << 30    ; Start with the highest even bit (30th bit for 32-bit numzpber)

        ; Find the highest bit position that is set
        while bit_pos > numzp
            bit_pos >>= 2

        ; Process pairs of bits from high to low
        while bit_pos != 0 {
            temp = res + bit_pos
            if numzp >= temp {
                numzp -= temp
                res = (res >> 1) + bit_pos
            }
            else {
                res >>= 1
            }
            bit_pos >>= 2
        }

        uword @nozp resultword = lsw(res)

        cx16.r6r7sl = popl()
        cx16.r4r5sl = popl()
        cx16.r2r3sl = popl()
        cx16.r0r1sl = popl()

        return resultword
    }
}
