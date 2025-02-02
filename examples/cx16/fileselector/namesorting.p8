
; Common routines used for sorting the file names alphabetically.
; note: cannot use the sorting library module because that relies on zeropage to be directly available (@requirezp pointer)
; and this code is meant to be able to being used without zeropage as well (except for R0-R15).

sorting {

    sub shellsort_pointers(uword stringpointers_array, ubyte num_elements) {
        ; Comparefunc must be a routine that accepts 2 pointers in R0 and R1, and must return with Carry=1 if R0<=R1, otherwise Carry=0.
        ; One such function, to compare strings, is provided as 'string_comparator' below.
        cx16.r2 = stringpointers_array      ; need zeropage pointer
        num_elements--
        ubyte gap
        for gap in [132, 57, 23, 10, 4, 1] {
            ubyte i
            for i in gap to num_elements {
                cx16.r1 = peekw(cx16.r2+i*$0002)
                ubyte @zp j = i
                ubyte @zp k = j-gap
                while j>=gap {
                    cx16.r0 = peekw(cx16.r2+k*2)
                    if string_comparator(cx16.r0, cx16.r1)
                        break
                    pokew(cx16.r2+j*2, cx16.r0)
                    j = k
                    k -= gap
                }
                pokew(cx16.r2+j*2, cx16.r1)
            }
        }

        asmsub string_comparator(uword string1 @R0, uword string2 @R1) -> bool @Pc {
            ; R0 and R1 are the two strings, must return Carry=1 when R0<=R1, else Carry=0
            %asm {{
                lda  cx16.r1L
                ldy  cx16.r1H
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  cx16.r0L
                ldy  cx16.r0H
                jsr  prog8_lib.strcmp_mem
                cmp  #1
                bne  +
                clc
                rts
+               sec
                rts
            }}
        }
    }
}
