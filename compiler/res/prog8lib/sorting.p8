; **experimental** data sorting routines, API subject to change!!

sorting {
    %option ignore_unused

    ; GNOME SORT is tiny and extremely fast if the initial values are already almost sorted.
    ; SHELL SORT is quite a bit faster if the initial values are more randomly distributed.

    ; NOTE: all word arrays are assumed to be @nosplit!!
    ; NOTE: sorting is done in ascending order!!!
    ; Note: could be made slightly faster by using modifying dcode for the CPY after _loop but that compromises romability

    asmsub gnomesort_ub(^^ubyte bytearray @AY, ubyte num_elements @X) {
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

    sub gnomesort_by_ub(^^ubyte @requirezp ub_keys, ^^uword wordvalues, ubyte num_elements) {
        ; sorts the 'wordvalues' array (no-split array of words) according to the 'ub_keys' array (which also gets sorted ofcourse).
        ubyte @zp pos=1
        while pos != num_elements {
            if ub_keys[pos]>=ub_keys[pos-1]
                pos++
            else {
                ; swap elements
                cx16.r0L = ub_keys[pos-1]
                ub_keys[pos-1] = ub_keys[pos]
                ub_keys[pos] = cx16.r0L
                ^^uword @requirezp vptr = wordvalues + (pos-1)
                cx16.r0 = peekw(vptr)
                pokew(vptr, peekw(vptr+1))
                pokew(vptr+1, cx16.r0)

                pos--
                if_z
                    pos++
            }
        }
    }

    ; TODO convert to ^^uword once code size regression is fixed
    sub gnomesort_uw(uword @requirezp values, ubyte num_elements) {
        ; Sorts the values array (no-split unsigned words).
        ; Max number of elements is 128. Clobbers R0 and R1.
        ubyte @zp pos=2
        num_elements *= 2
        while pos != num_elements {
            cx16.r1L = pos-2
            if peekw(values+pos) >= peekw(values + cx16.r1L)
                pos += 2
            else {
                ; swap elements
                cx16.r0 = peekw(values + cx16.r1L)
                pokew(values + cx16.r1L, peekw(values + pos))
                pokew(values + pos, cx16.r0)
                pos-=2
                if_z
                    pos+=2
            }
        }
    }

    ; TODO convert to ^^uword once code size regression is fixed
    sub gnomesort_by_uw(uword @requirezp uw_keys, uword wordvalues, ubyte num_elements) {
        ; Sorts the 'wordvalues' array according to the 'uw_keys' array (which also gets sorted ofcourse).
        ; both arrays should be no-split array of words. uw_keys are unsigned.
        ; Max number of elements is 128. Clobbers R0 and R1.
        ubyte @zp pos=2
        num_elements *= 2
        while pos != num_elements {
            cx16.r1L = pos-2
            if peekw(uw_keys+pos) >= peekw(uw_keys + cx16.r1L)
                pos += 2
            else {
                ; swap elements
                cx16.r0 = peekw(uw_keys + cx16.r1L)
                pokew(uw_keys + cx16.r1L, peekw(uw_keys+ pos))
                pokew(uw_keys + pos, cx16.r0)
                cx16.r0 = peekw(wordvalues + cx16.r1L)
                pokew(wordvalues + cx16.r1L, peekw(wordvalues + pos))
                pokew(wordvalues + pos, cx16.r0)

                pos-=2
                if_z
                    pos+=2
            }
        }
    }

    ; gnomesort_pointers is not worth it over shellshort_pointers.

    sub shellsort_ub(^^ubyte @requirezp values, ubyte num_elements) {
        ; sorts the values array (unsigned bytes).
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

    ; TODO convert to ^^uword once code size regression is fixed
    sub shellsort_uw(uword @requirezp values, ubyte num_elements) {
        ; sorts the values array (no-split unsigned words).
        num_elements--
        ubyte gap
        for gap in [132, 57, 23, 10, 4, 1] {
            ubyte i
            for i in gap to num_elements {
                uword @zp temp = peekw(values+i*$0002)
                ubyte @zp j = i
                ubyte @zp k = j-gap
                while j>=gap {
                    uword @zp v = peekw(values+k*$0002)
                    if v <= temp break
                    pokew(values+j*$0002, v)
                    j = k
                    k -= gap
                }
                pokew(values+j*$0002, temp)
            }
        }
    }

    ; TODO convert to ^^uword once code size regression is fixed
    sub shellsort_by_ub(^^ubyte @requirezp ub_keys, uword @requirezp wordvalues, ubyte num_elements) {
        ; sorts the 'wordvalues' array (no-split array of words) according to the 'ub_keys' array (which also gets sorted ofcourse).
        num_elements--
        ubyte @zp gap
        for gap in [132, 57, 23, 10, 4, 1] {
            ubyte i
            for i in gap to num_elements {
                ubyte @zp temp = ub_keys[i]
                uword temp_wv = peekw(wordvalues + i*$0002)
                ubyte @zp j = i
                ubyte @zp k = j-gap
                repeat {
                    ubyte @zp v = ub_keys[k]
                    if v <= temp break
                    if j < gap break
                    ub_keys[j] = v
                    pokew(wordvalues + j*$0002, peekw(wordvalues + k*$0002))
                    j = k
                    k -= gap
                }
                ub_keys[j] = temp
                pokew(wordvalues + j*$0002, temp_wv)
            }
        }
    }

    ; TODO convert to ^^uword once code size regression is fixed
    sub shellsort_by_uw(uword @requirezp uw_keys, uword @requirezp wordvalues, ubyte num_elements) {
        ; sorts the 'wordvalues' array according to the 'uw_keys' array (which also gets sorted ofcourse).
        ; both arrays should be no-split array of words. uw_keys are unsigned.
        num_elements--
        ubyte gap
        for gap in [132, 57, 23, 10, 4, 1] {
            ubyte i
            for i in gap to num_elements {
                uword @zp temp = peekw(uw_keys+i*$0002)
                uword temp_wv = peekw(wordvalues + i*$0002)
                ubyte @zp j = i
                ubyte @zp k = j-gap
                while j>=gap {
                    uword @zp v = peekw(uw_keys+k*2)
                    if v <= temp break
                    pokew(uw_keys+j*2, v)
                    pokew(wordvalues + j*$0002, peekw(wordvalues + k*$0002))
                    j = k
                    k -= gap
                }
                pokew(uw_keys+j*2, temp)
                pokew(wordvalues + j*$0002, temp_wv)
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
                    cx16.r0 = peekw(pointers+k*$0002)
                    void call(comparefunc)
                    if_cs break
                    pokew(pointers+j*$0002, cx16.r0)
                    j = k
                    k -= gap
                }
                pokew(pointers+j*$0002, cx16.r1)
            }
        }
    }

    asmsub string_comparator(str string1 @R0, str string2 @R1) -> bool @Pc {
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
