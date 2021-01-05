%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
        str[] binstrings = [
            "",
            "%",
            "%0",
            "$0",
            "%1",
            "101",
            "%11111111",
            "%1111    ",
            "%  1111    ",
            "%1111%",
            "%1111)",
            "%1111,",
            "%1111.",
            "%1111foo",
            "%1111000010101010",
            "%1111000010101010111"
        ]
        str[] hexstrings = [
            "",
            "$",
            "$0",
            "%0",
            "$1",
            "$9",
            "9934",
            "$91",
            "$91    ",
            "$  91    ",
            "$12ae$",
            "$12ae)",
            "$12ae,",
            "$12ae.",
            "$12aez00",
            "$12345"
        ]
        str[] posdecstrings = [
            "",
            "0",
            "1",
            "9",
            "$9",
            "10",
            "11",
            "123",
            "255",
            "62221",
            "42    ",
            "  42    ",
            "42$",
            "42)",
            "42,",
            "42.",
            "42aaaa"
        ]
        str[] worddecstrings = [
            "",
            "0",
            "1",
            "9",
            "$9",
            "10",
            "11",
            "123",
            "255",
            "62221",
            "42    ",
            "  42    ",
            "42$",
            "42)",
            "42,",
            "42.",
            "42aaaa",
            "-",
            "-0",
            "-1",
            "-9",
            "$9",
            "-10",
            "-11",
            "-123",
            "-255",
            "-62221",
            "-32221",
            "-42    ",
            "-  42    ",
            "-42-",
            "-42$",
            "-42)",
            "-42,",
            "-42.",
            "-42aaaa"
        ]

        uword value
        word wvalue
        uword strptr
        for strptr in binstrings {
            value = conv.bin2uword(strptr)
            txt.print(strptr)
            txt.print(" = ")
            txt.print_uw(value)
            txt.print(" = ")
            txt.print_uwbin(value, true)
            txt.print("  #")
            txt.print_uw(cx16.r15)  ; number of chars processedc
            txt.chrout('\n')
        }

        txt.chrout('\n')
        for strptr in hexstrings {
            value = conv.hex2uword(strptr)
            txt.print(strptr)
            txt.print(" = ")
            txt.print_uw(value)
            txt.print(" = ")
            txt.print_uwhex(value, true)
            txt.print("  #")
            txt.print_uw(cx16.r15)  ; number of chars processedc
            txt.chrout('\n')
        }

        txt.chrout('\n')
        for strptr in posdecstrings {
            value = conv.str2uword(strptr)
            txt.print(strptr)
            txt.print(" = ")
            txt.print_uw(value)
            txt.print("  #")
            txt.print_uw(cx16.r15)  ; number of chars processedc
            txt.chrout('\n')
        }

        txt.chrout('\n')
        for strptr in worddecstrings {
            wvalue = conv.str2word(strptr)
            txt.print(strptr)
            txt.print(" = ")
            txt.print_w(wvalue)
            txt.print("  #")
            txt.print_uw(cx16.r15)  ; number of chars processedc
            txt.chrout('\n')
        }


;        found = strfind("irmen de jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfind(" irmen-de-jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfind("irmen-de-jong ", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfind("irmen-de-jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')

;        found = strfinds("irmen de jong", "de")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfinds("irmen de jong", "irmen")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfinds("irmen de jong", "jong")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfinds("irmen de jong", "de456")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
    }
}
