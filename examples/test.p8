%import textio
%import strings
%zeropage basicsafe

main {
    sub start() {
        str name1 = iso:"Irmen De Jong"
        str name2 = petscii:"Irmen De Jong"

        ;txt.iso()
        txt.print(name1)
        txt.nl()
        txt.print(name2)
        txt.nl()

        txt.print_b(strings.compare(name1,iso:"Irmen De Jong"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen De Jona"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen Z"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen De Jong ZZZ"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen AAA"))
        txt.spc()
        txt.spc()
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen DE JONG"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen DE JONA"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"IRMEN Z"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen DE JONG ZZZ"))
        txt.spc()
        txt.print_b(strings.compare(name1,iso:"Irmen AAA"))
        txt.nl()

        txt.print_b(strings.compare_nocase_iso(name1,iso:"Irmen De Jong"))
        txt.spc()
        txt.print_b(strings.compare_nocase_iso(name1,iso:"Irmen De Jona"))
        txt.spc()
        txt.print_b(strings.compare_nocase_iso(name1,iso:"Irmen Z"))
        txt.spc()
        txt.print_b(strings.compare_nocase_iso(name1,iso:"Irmen De Jong ZZZ"))
        txt.spc()
        txt.print_b(strings.compare_nocase_iso(name1,iso:"Irmen AAA"))
        txt.spc()
        txt.spc()
        txt.spc()
        txt.print_b(strings.compare_nocase(name1,iso:"Irmen DE JONG"))       ; 0
        txt.spc()
        txt.print_b(strings.compare_nocase(name1,iso:"Irmen DE JONA"))       ; 1
        txt.spc()
        txt.print_b(strings.compare_nocase(name1,iso:"IRMEN Z"))             ; -1
        txt.spc()
        txt.print_b(strings.compare_nocase(name1,iso:"Irmen DE JONG ZZZ"))   ; -1
        txt.spc()
        txt.print_b(strings.compare_nocase(name1,iso:"Irmen AAA"))           ; 1
        txt.nl()
        txt.nl()
        txt.print_b(strings.compare_nocase(iso:"halloDAN",iso:"HALLOdan"))
        txt.spc()
        txt.print_b(strings.compare_nocase_iso(iso:"halloDAN",iso:"HALLOdan"))
        txt.spc()
        txt.spc()
        txt.spc()
        txt.print_b(strings.compare_nocase(petscii:"halloDAN",petscii:"HALLOdan"))
        txt.spc()
        txt.print_b(strings.compare_nocase_iso(petscii:"halloDAN",petscii:"HALLOdan"))
        txt.nl()
        txt.nl()


        txt.print(name1)
        txt.nl()
        txt.print(name2)
        txt.nl()
    }
}

mystrings {

    sub compare_nocase(str s1, str s2) -> byte {
        cx16.r1L = 0
        repeat {
            cx16.r0L = s1[cx16.r1L]
            if_z {
                if s2[cx16.r1L] == 0
                    return 0
                else
                    return -1
            }
            cx16.r0H = s2[cx16.r1L]
            if_z
                return 1
            cmp(strings.lowerchar(cx16.r0L), strings.lowerchar(cx16.r0H))
            if_neg
                return -1
            if_nz
                return 1

            cx16.r1L++
        }
    }

    sub compare_nocase_iso(str s1, str s2) -> byte {
        cx16.r1L = 0
        repeat {
            cx16.r0L = s1[cx16.r1L]
            if_z {
                if s2[cx16.r1L] == 0
                    return 0
                else
                    return -1
            }
            cx16.r0H = s2[cx16.r1L]
            if_z
                return 1
            cmp(strings.lowerchar_iso(cx16.r0L), strings.lowerchar_iso(cx16.r0H))
            if_neg
                return -1
            if_nz
                return 1

            cx16.r1L++
        }
    }
}
