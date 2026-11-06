; Amiga (m68k) sorting routines, kept in Prog8 for now.
; Word and long data only. API subject to change.

sorting {
    %option merge, ignore_unused


    ; GNOME SORT is tiny and extremely fast if the initial values are already almost sorted.
    ; SHELL SORT is quite a bit faster if the initial values are more randomly distributed.

    sub gnomesort_uw(^^uword values, uword num_elements) {
        if num_elements <= 1 return
        uword pos = 1
        while pos < num_elements {
            if values[pos] < values[pos-1] {
                uword tmp = values[pos-1]
                values[pos-1] = values[pos]
                values[pos] = tmp
                if pos > 1
                    pos--
                else
                    pos++
            } else {
                pos++
            }
        }
    }

    sub gnomesort_by_uw(^^uword keys, ^^uword values, uword num_elements) {
        if num_elements <= 1 return
        uword pos = 1
        while pos < num_elements {
            if keys[pos] < keys[pos-1] {
                uword tmpk = keys[pos-1]
                keys[pos-1] = keys[pos]
                keys[pos] = tmpk
                uword tmpv = values[pos-1]
                values[pos-1] = values[pos]
                values[pos] = tmpv
                if pos > 1
                    pos--
                else
                    pos++
            } else {
                pos++
            }
        }
    }

    sub gnomesort_ul(^^long values, uword num_elements) {
        if num_elements <= 1 return
        uword pos = 1
        while pos < num_elements {
            if values[pos] < values[pos-1] {
                long tmp = values[pos-1]
                values[pos-1] = values[pos]
                values[pos] = tmp
                if pos > 1
                    pos--
                else
                    pos++
            } else {
                pos++
            }
        }
    }

    sub gnomesort_by_ul(^^long keys, ^^long values, uword num_elements) {
        if num_elements <= 1 return
        uword pos = 1
        while pos < num_elements {
            if keys[pos] < keys[pos-1] {
                long tmpk = keys[pos-1]
                keys[pos-1] = keys[pos]
                keys[pos] = tmpk
                long tmpv = values[pos-1]
                values[pos-1] = values[pos]
                values[pos] = tmpv
                if pos > 1
                    pos--
                else
                    pos++
            } else {
                pos++
            }
        }
    }

    sub shellsort_uw(^^uword values, uword num_elements) {
        if num_elements <= 1 return
        ubyte gi
        for gi in 0 to 8 {
            uword gap = gaps[gi]
            if gap >= num_elements continue
            uword i
            for i in gap to num_elements-1 {
                uword temp = values[i]
                uword j = i
                while j >= gap and values[j-gap] > temp {
                    values[j] = values[j-gap]
                    j -= gap
                }
                values[j] = temp
            }
        }
    }

    sub shellsort_by_uw(^^uword keys, ^^uword values, uword num_elements) {
        if num_elements <= 1 return
        ubyte gi
        for gi in 0 to 8 {
            uword gap = gaps[gi]
            if gap >= num_elements continue
            uword i
            for i in gap to num_elements-1 {
                uword tempk = keys[i]
                uword tempv = values[i]
                uword j = i
                while j >= gap and keys[j-gap] > tempk {
                    keys[j] = keys[j-gap]
                    values[j] = values[j-gap]
                    j -= gap
                }
                keys[j] = tempk
                values[j] = tempv
            }
        }
    }

    sub shellsort_ul(^^long values, uword num_elements) {
        if num_elements <= 1 return
        ubyte gi
        for gi in 0 to 8 {
            uword gap = gaps[gi]
            if gap >= num_elements continue
            uword i
            for i in gap to num_elements-1 {
                long temp = values[i]
                uword j = i
                while j >= gap and values[j-gap] > temp {
                    values[j] = values[j-gap]
                    j -= gap
                }
                values[j] = temp
            }
        }
    }

    sub shellsort_by_ul(^^long keys, ^^long values, uword num_elements) {
        if num_elements <= 1 return
        ubyte gi
        for gi in 0 to 8 {
            uword gap = gaps[gi]
            if gap >= num_elements continue
            uword i
            for i in gap to num_elements-1 {
                long tempk = keys[i]
                long tempv = values[i]
                uword j = i
                while j >= gap and keys[j-gap] > tempk {
                    keys[j] = keys[j-gap]
                    values[j] = values[j-gap]
                    j -= gap
                }
                keys[j] = tempk
                values[j] = tempv
            }
        }
    }

    private uword[9] gaps = [1750, 701, 301, 132, 57, 23, 10, 4, 1]

}
