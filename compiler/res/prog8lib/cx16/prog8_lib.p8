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

    sub profile_sub_entry(str routinename) {
        ; routine that is called at the start of each subroutine, when the profiling instrumentation is enabled
        ; it prints Stackpointer, CPU cycles, routine name  to the emulator's console output (cx16 target only)
        ; arguments in:
        ; X = stack pointer value
        ; AY = pointer to routine name string
        %asm {{
            ; save r0 and r1
            lda  cx16.r0
            ldy  cx16.r0+1
            pha
            phy
            lda  cx16.r1
            ldy  cx16.r1+1
            pha
            phy
            txa  ; stackpointer
            jsr  str_ubhex
        }}

        &ubyte EMU_CHROUT = $9fb0 + 11    ; write: outputs as character to console
        &ubyte EMU_EMU_DETECT1 = $9fb0 + 14
        &ubyte EMU_EMU_DETECT2 = $9fb0 + 15
        &ubyte EMU_CPUCLK_L = $9fb0 + 8
        &ubyte EMU_CPUCLK_M = $9fb0 + 9
        &ubyte EMU_CPUCLK_H = $9fb0 + 10
        &ubyte EMU_CPUCLK_U = $9fb0 + 11

        if EMU_EMU_DETECT1=='1' and EMU_EMU_DETECT2=='6' {
            sys.irqsafe_set_irqd()
            emu_console_write(buffer)
            EMU_CHROUT = iso:','
            str_ulhex(emu_cpu_cycles())
            emu_console_write(buffer)
            EMU_CHROUT = iso:','
            emu_console_write(routinename)
            EMU_CHROUT = 10     ; newline
            sys.irqsafe_clear_irqd()
        }

        %asm {{
            ; restore r1 and r0
            ply
            pla
            sta  cx16.r1
            sty  cx16.r1+1
            ply
            pla
            sta  cx16.r0
            sty  cx16.r0+1
            rts
        }}

        str @shared buffer = "00000000000"

        asmsub emu_cpu_cycles() -> long @R0R1 {
            ; -- returns the 32 bits cpu clock counter in R1:R0
            %asm {{
                lda  EMU_CPUCLK_L
                sta  cx16.r0L
                lda  EMU_CPUCLK_M
                sta  cx16.r0H
                lda  EMU_CPUCLK_H
                sta  cx16.r1L
                lda  EMU_CPUCLK_U
                sta  cx16.r1H
                rts
            }}
        }

        asmsub emu_console_write(str isoString @AY) {
            %asm {{
                sta  cx16.r0
                sty  cx16.r0+1
                ldy  #0
-               lda  (cx16.r0),y
                beq  +
                sta  EMU_CHROUT
                iny
                bra  -
+               rts
            }}
        }

        asmsub  str_ubhex(ubyte value @ A) clobbers(X) {
            ; ---- convert the ubyte in A in hex string form
            %asm {{
                jsr  internal_ubyte2hex
                sta  buffer
                sty  buffer+1
                lda  #0
                sta  buffer+2
                rts
            }}
        }

        sub str_ulhex(long value) {
            %asm {{
                lda  value+3
                jsr  internal_ubyte2hex
                sta  buffer
                sty  buffer+1
                lda  value+2
                jsr  internal_ubyte2hex
                sta  buffer+2
                sty  buffer+3
                lda  value+1
                jsr  internal_ubyte2hex
                sta  buffer+4
                sty  buffer+5
                lda  value
                jsr  internal_ubyte2hex
                sta  buffer+6
                sty  buffer+7
                lda  #0
                sta  buffer+8
                rts
            }}
        }

        asmsub  internal_ubyte2hex  (ubyte value @A) clobbers(X) -> ubyte @A, ubyte @Y  {
            ; ---- A to hex petscii string in AY (first hex char in A, second hex char in Y)
            %asm {{
                pha
                and  #$0f
                tax
                ldy  _hex_digits,x
                pla
                lsr  a
                lsr  a
                lsr  a
                lsr  a
                tax
                lda  _hex_digits,x
                rts

        _hex_digits	.text "0123456789abcdef"	; can probably be reused for other stuff as well
                ; !notreached!
            }}
        }
    }
}
