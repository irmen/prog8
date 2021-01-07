%import test_stack
%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {

    ; TODO: error when a parameter has the same name as an existing module/block/subroutine:  sub print_right(ubyte width, uword string) {


    sub start() {
        str s1 = "irmen"
        str s2 = "what"
        str s3 = "irmen2"

        s3[5] = 0

        txt.print("length:\n")
        txt.print_ub(len(s1))
        txt.chrout('\n')
        txt.print_ub(string.length(s1))

        txt.print("\n\ncopy:\n")
        txt.print(s3)
        txt.chrout('\n')
        s3 = s2
        txt.print(s3)
        txt.chrout('\n')
        txt.print_ub(string.copy("new", s3))
        txt.print(s3)

        txt.print("\n\ncompare:\n")
        txt.chrout('\n')
        txt.print_ub(string.compare(s1, s2))
        txt.chrout('\n')
        txt.print_ub(string.compare(s2, s1))
        txt.chrout('\n')
        txt.print_ub(string.compare(s1, s3))
        txt.chrout('\n')
        txt.chrout('\n')
        txt.print_ub(s1==s2)
        txt.chrout('\n')
        txt.print_ub(s1<s2)
        txt.chrout('\n')
        txt.print_ub(s1>s2)
        txt.chrout('\n')

        txt.print("\n\nleft:")
        string.left(s2,2,s3)
        txt.print(s3)
        txt.chrout('\n')
        txt.print("\n\nright:\n")
        txt.print(s2)
        txt.chrout('\n')
        string.right(s2,2,s3)
        txt.print(s3)
        txt.chrout('\n')

        txt.print("\n\nfind:\n")
        txt.print(s1)
        txt.chrout('\n')
        uword found = string.find(s1, 'e')
        txt.print_uwhex(found, 1)
        if found
            txt.print(found)
        txt.chrout('\n')
        found = string.find(s1, 'i')
        txt.print_uwhex(found, 1)
        if found
            txt.print(found)
        txt.chrout('\n')
        found = string.find(s1, 'x')
        txt.print_uwhex(found, 1)
        if found
            txt.print(found)
        txt.chrout('\n')

        txt.print("\n\nslice:\n")
        string.slice(s1, 0, 5, s2)
        txt.print(s2)
        txt.chrout('\n')
        string.slice(s1, 1, 4, s2)
        txt.print(s2)
        txt.chrout('\n')
        string.slice(s1, 2, 2, s2)
        txt.print(s2)
        txt.chrout('\n')
        string.slice(s1, 3, 2, s2)
        txt.print(s2)
        txt.chrout('\n')

        test_stack.test()
    }

    sub start2 () {
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


;        found = string.find("irmen de jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = string.find(" irmen-de-jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = string.find("irmen-de-jong ", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = string.find("irmen-de-jong", ' ')
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
