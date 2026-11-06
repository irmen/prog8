; m68k sorting demo: uses the shared_m68k_sorting routines
; and prints the sorted results.

%import textio
%import sorting

main {
    sub start() {
        ; gnome sort (word)
        uword[16] vals = [15, 3, 9, 1, 7, 12, 8, 4, 11, 2, 6, 14, 13, 5, 10, 0]
        sorting.gnomesort_uw(vals, 16)
        txt.print("gnomesort_uw: ")
        ubyte i
        for i in 0 to 15 {
            txt.print_uw(vals[i])
            txt.print(" ")
        }
        txt.nl()

        ; gnome sort by key (word keys + word values)
        uword[16] keys = [15, 3, 9, 1, 7, 12, 8, 4, 11, 2, 6, 14, 13, 5, 10, 0]
        uword[16] data = [150, 30, 90, 10, 70, 120, 80, 40, 110, 20, 60, 140, 130, 50, 100, 0]
        sorting.gnomesort_by_uw(keys, data, 16)
        txt.print("gnomesort_by_uw keys: ")
        for i in 0 to 15 {
            txt.print_uw(keys[i])
            txt.print(" ")
        }
        txt.nl()
        txt.print("gnomesort_by_uw data: ")
        for i in 0 to 15 {
            txt.print_uw(data[i])
            txt.print(" ")
        }
        txt.nl()

        ; shell sort (word) on fresh data
        uword[16] v2 = [15, 3, 9, 1, 7, 12, 8, 4, 11, 2, 6, 14, 13, 5, 10, 0]
        sorting.shellsort_uw(v2, 16)
        txt.print("shellsort_uw: ")
        for i in 0 to 15 {
            txt.print_uw(v2[i])
            txt.print(" ")
        }
        txt.nl()

        ; shell sort by key (word) on fresh data
        uword[16] k2 = [15, 3, 9, 1, 7, 12, 8, 4, 11, 2, 6, 14, 13, 5, 10, 0]
        uword[16] d2 = [150, 30, 90, 10, 70, 120, 80, 40, 110, 20, 60, 140, 130, 50, 100, 0]
        sorting.shellsort_by_uw(k2, d2, 16)
        txt.print("shellsort_by_uw keys: ")
        for i in 0 to 15 {
            txt.print_uw(k2[i])
            txt.print(" ")
        }
        txt.nl()

        ; long demo
        long[8] lvals = [400000, 100000, 300000, 200000, 800000, 500000, 700000, 600000]
        sorting.gnomesort_ul(lvals, 8)
        txt.print("gnomesort_ul: ")
        for i in 0 to 7 {
            txt.print_l(lvals[i])
            txt.print(" ")
        }
        txt.nl()

        long[8] lk = [400000, 100000, 300000, 200000, 800000, 500000, 700000, 600000]
        long[8] ld = [8, 7, 6, 5, 4, 3, 2, 1]
        sorting.shellsort_by_ul(lk, ld, 8)
        txt.print("shellsort_by_ul keys: ")
        for i in 0 to 7 {
            txt.print_l(lk[i])
            txt.print(" ")
        }
        txt.nl()

        sys.exit(0)
    }
}