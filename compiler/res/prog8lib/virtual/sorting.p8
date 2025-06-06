; **experimental** data sorting routines, API subject to change!!

; NOTE: gnomesort is not implemented here, just use shellshort.

sorting {
    %option ignore_unused

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


    sub shellsort_by_ub(uword @requirezp ub_keys, uword @requirezp wordvalues, ubyte num_elements) {
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
}
