; **experimental** data sorting routines, API subject to change!!

sorting {
    %option ignore_unused

    ; GNOME SORT is tiny and extremely fast if the initial values are already almost sorted.
    ; SHELL SORT is quite a bit faster if the initial values are more randomly distributed.

    ; NOTE: all word arrays are assumed to be @nosplit!!
    ; NOTE: sorting is done in ascending order!!!
    ; Note: could be made slightly faster by using modifying dcode for the CPY after _loop but that compromises romability

    asmsub gnomesort_ub(uword bytearray @AY, ubyte num_elements @X) {
        %asm {{
            stx  P8ZP_SCRATCH_REG
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            sta  P8ZP_SCRATCH_W2
            cmp  #0
            bne  +
            dey
+           dec  P8ZP_SCRATCH_W2
            sty  P8ZP_SCRATCH_W2+1
            ldy  #1     ; pos
_loop
            cpy  P8ZP_SCRATCH_REG
            beq  _done
            lda  (P8ZP_SCRATCH_W1),y
            cmp  (P8ZP_SCRATCH_W2),y
            bcs  +
            ; swap elements
            tax
            lda  (P8ZP_SCRATCH_W2),y
            sta  (P8ZP_SCRATCH_W1),y
            txa
            sta  (P8ZP_SCRATCH_W2),y
            dey
            bne  _loop
+           iny
            bne  _loop
_done
            rts
        }}
    }

    /*
    prog8 source code for the above routine:

    sub gnomesort_ub(uword @requirezp values, ubyte num_elements) {
        ubyte @zp pos=1
        while pos != num_elements {
            if values[pos]>=values[pos-1]
                pos++
            else {
                ; swap elements
                cx16.r0L = values[pos-1]
                values[pos-1] = values[pos]
                values[pos] = cx16.r0L
                pos--
                if_z
                    pos++
            }
        }
    }
    */

    sub gnomesort_uw(uword values, ubyte num_elements) {
        ; TODO optimize this more, rewrite in asm?
        ubyte @zp pos = 1
        uword @requirezp ptr = values+2
        while pos != num_elements {
            cx16.r0 = peekw(ptr-2)
            cx16.r1 = peekw(ptr)
            if cx16.r0<=cx16.r1 {
                pos++
                ptr+=2
            }
            else {
                ; swap elements
                pokew(ptr-2, cx16.r1)
                pokew(ptr, cx16.r0)
                if pos>1 {
                    pos--
                    ptr-=2
                }
            }
        }
    }

    ; gnomesort_pointers is not worth it over shellshort_pointers.

    sub shellsort_ub(uword @requirezp values, ubyte num_elements) {
        num_elements--
        ubyte @zp gap
        for gap in [132, 57, 23, 10, 4, 1] {
            ubyte i
            for i in gap to num_elements {
                ubyte @zp temp = values[i]
                ubyte @zp j = i
                ubyte @zp k = j-gap
                repeat {
                    ubyte @zp v = values[k]
                    if v <= temp break
                    if j < gap break
                    values[j] = v
                    j = k
                    k -= gap
                }
                values[j] = temp
            }
        }
    }

    sub shellsort_uw(uword @requirezp values, ubyte num_elements) {
        num_elements--
        ubyte gap
        for gap in [132, 57, 23, 10, 4, 1] {
            ubyte i
            for i in gap to num_elements {
                uword @zp temp = peekw(values+i*$0002)
                ubyte @zp j = i
                ubyte @zp k = j-gap
                while j>=gap {
                    uword @zp v = peekw(values+k*2)
                    if v <= temp break
                    pokew(values+j*2, v)
                    j = k
                    k -= gap
                }
                pokew(values+j*2, temp)
            }
        }
    }

    sub shellsort_pointers(uword @requirezp pointers, ubyte num_elements, uword comparefunc) {
        ; Comparefunc must be a routine that accepts 2 pointers in R0 and R1, and must return with Carry=1 if R0<=R1, otherwise Carry=0.
        ; One such function, to compare strings, is provided as 'string_comparator' below.
        num_elements--
        ubyte gap
        for gap in [132, 57, 23, 10, 4, 1] {
            ubyte i
            for i in gap to num_elements {
                cx16.r1 = peekw(pointers+i*$0002)
                ubyte @zp j = i
                ubyte @zp k = j-gap
                while j>=gap {
                    cx16.r0 = peekw(pointers+k*2)
                    void call(comparefunc)
                    if_cs break
                    pokew(pointers+j*2, cx16.r0)
                    j = k
                    k -= gap
                }
                pokew(pointers+j*2, cx16.r1)
            }
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
+           sec
            rts
        }}
    }

}
