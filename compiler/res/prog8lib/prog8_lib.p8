; Internal library routines - always included by the compiler

prog8_lib {
    %option no_symbol_prefixing, ignore_unused

	%asminclude "library:prog8_lib.asm"
	%asminclude "library:prog8_funcs.asm"


    sub sqrt_long(long num) -> uword {
        ;    Calculate the square root of a 4-byte long value using bitwise operations.
        ; NOTE: this is not in prog8_math because all the other sqrt routines are in prog8_funcs.asm here too

        if num <= 0
            return 0

        ;    Start with the most significant pair of bits
        ;    For the digit-by-digit algorithm, we process the number in groups of 2 bits
        long res = 0
        long @zp bit_pos = 1 << 30    ; Start with the highest even bit (30th bit for 32-bit number)

        ; Find the highest bit position that is set
        while bit_pos > num
            bit_pos >>= 2

        ; Process pairs of bits from high to low
        while bit_pos != 0 {
            long temp = res + bit_pos
            if num >= temp {
                num -= temp
                res = (res >> 1) + bit_pos
            }
            else {
                res >>= 1
            }
            bit_pos >>= 2
        }

        return lsw(res)
    }
}
