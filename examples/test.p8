%import textio
%import string
%option no_sysinit
%zeropage basicsafe


main {
    str large1 = "the quick brown fox jumps over the lazy dog. the quick brown fox jumps over the lazy dog. the quick brown fox jumps over the lazy dog. the quick brown fox jumps over the lazy dog."
    str large2 = "the quick brown fox jumps over the lazy dog. the quick brown fox jumps over the lazy dog. the quick brown fox jumps over the lazy dog. the quick brown fox jumps over the laxx doggo doggo."

    sub start() {
        txt.nl()
        check("", "", 0)
        check("", "a", -1)
        check("a", "", 1)
        check("a", "a", 0)
        check("a", "z", -1)
        check("z", "a", 1)
        check("irmen", "irmen", 0)
        check("irmen", "irmen2", -1)
        check("irmen2", "irmen", 1)
        check("irmen", "irxen", -1)
        check("irmen", "irman", 1)
        txt.nl()

        bench()     ; orig: 88   (pet: 713)      optimized:   56  451
        bench2()    ; orig: 131  (pet: 1066)     optimized:   83  674
    }

    sub bench2() {
        cbm.SETTIM(0,0,0)
        repeat 1000 {
            bool compare = large1 != large2
            cx16.r0L++
            compare = large1 > large2
            cx16.r0L++
            compare = large1 <= large2
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
    }

    sub bench() {
        cbm.SETTIM(0,0,0)
        repeat 2000 {
            void string.compare(large1,large2)
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
    }

    sub check(str s1, str s2, byte expected) {
        byte result = string.compare(s1, s2)
        txt.print(s1)
        txt.print(" & ")
        txt.print(s2)
        txt.print(": ")
        txt.print_b(result)
        if result!=expected
            txt.print("  !wrong!\n")
        else
            txt.nl()
    }
}
