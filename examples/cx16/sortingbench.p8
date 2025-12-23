%import math
%import textio
%import strings
%import sorting
%import emudbg

; show the use and times the performance of the routines in the sorting module.

main {
    ubyte[50] array1
    ubyte[50] array2
    uword[50] @nosplit warray1
    uword[50] @nosplit warray2
    str[22] @nosplit fruits
    str[] @nosplit original_fruits = ["mango", "banana", "cranberry", "zucchini", "blackberry", "orange", "dragonfruit", "cherry",
            "kiwifruit", "lychee", "peach", "apricot", "tomato", "avocado", "nectarine", "pear",
            "mulberry", "pineapple", "apple", "starfruit", "pumpkin", "coconut"]

    sub fill_arrays() {
        math.rndseed(999,1234)
        for cx16.r0L in 0 to len(array1)-1 {
            array1[cx16.r0L] = math.rnd()
            array2[cx16.r0L] = cx16.r0L & 127
            warray1[cx16.r0L] = math.rndw()
            warray2[cx16.r0L] = cx16.r0L * (100 as uword)
        }
        array2[40] = 200
        array2[44] = 201
        array2[48] = 202

        warray2[40] = 9900
        warray2[44] = 9910
        warray2[48] = 9920

        sys.memcopy(original_fruits, fruits, sizeof(original_fruits))
    }

    sub perf_reset() {
        emudbg.reset_cpu_cycles()
    }

    sub perf_print() {
        long cycles = emudbg.cpu_cycles()
        txt.print_ulhex(cycles, true)
        txt.nl()
    }

    sub start() {
        fill_arrays()

        txt.print("\ngnomesort random:\n")
        perf_reset()
        sorting.gnomesort_ub(array1, len(array1))
        perf_print()
        for cx16.r0L in 0 to len(array1)-1 {
            txt.print_ub(array1[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\ngnomesort almost sorted:\n")
        perf_reset()
        sorting.gnomesort_ub(array2, len(array2))
        perf_print()
        for cx16.r0L in 0 to len(array2)-1 {
            txt.print_ub(array2[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        fill_arrays()

        txt.print("\nshellsort:\n")
        perf_reset()
        sorting.shellsort_ub(array1, len(array1))
        perf_print()
        for cx16.r0L in 0 to len(array1)-1 {
            txt.print_ub(array1[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\nshellsort almost sorted:\n")
        perf_reset()
        sorting.shellsort_ub(array2, len(array2))
        perf_print()
        for cx16.r0L in 0 to len(array2)-1 {
            txt.print_ub(array2[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

;         txt.print("\n\npress enter for next page ")
;         void cbm.CHRIN()
;         txt.cls()

        txt.print("\ngnomesort (words):\n")
        perf_reset()
        sorting.gnomesort_uw(warray1, len(warray1))
        perf_print()
        for cx16.r0L in 0 to len(warray1)-1 {
            txt.print_uw(warray1[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\ngnomesort (words) almost sorted:\n")
        perf_reset()
        sorting.gnomesort_uw(warray2, len(warray2))
        perf_print()
        for cx16.r0L in 0 to len(warray2)-1 {
            txt.print_uw(warray2[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        fill_arrays()

        txt.print("\nshellsort (words):\n")
        perf_reset()
        sorting.shellsort_uw(warray1, len(warray1))
        perf_print()
        for cx16.r0L in 0 to len(warray1)-1 {
            txt.print_uw(warray1[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\nshellsort (words) almost sorted:\n")
        perf_reset()
        sorting.shellsort_uw(warray2, len(warray2))
        perf_print()
        for cx16.r0L in 0 to len(warray2)-1 {
            txt.print_uw(warray2[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\nshellsort (strings):\n")
        perf_reset()
        sorting.shellsort_pointers(fruits, len(fruits), sorting.string_comparator)
        perf_print()
        for cx16.r0L in 0 to len(fruits)-1 {
            txt.print(fruits[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\n\nend.")

        repeat {
        }
    }
}
