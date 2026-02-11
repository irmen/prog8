%import textio
%import conv
%import strings
%zeropage basicsafe

main {
    ; Test the routine
    sub start() {
        str test_string1 = "12345"
        str test_string2 = "-98765"
        str test_string3 = "0"
        str test_string4 = "2147483647"  ; Max positive 32-bit signed integer
        str test_string5 = "-2147483648" ; Min negative 32-bit signed integer
        str test_string6 = "42"
        str test_string7 = "1000000000"  ; Large number
        str test_string8 = ""             ; Empty string
        str test_string9 = "abc123"     ; Invalid string
        str test_string10 = "255"        ; Max unsigned byte
        str test_string11 = "127"        ; Max signed byte
        str test_string12 = "-128"       ; Min signed byte
        str test_string13 = "65535"      ; Max unsigned word
        str test_string14 = "32767"      ; Max signed word
        str test_string15 = "-32768"     ; Min signed word
        str test_string16 = "100000"     ; Just over 65535/10
        str test_string17 = "999999"     ; Larger number
        str test_string18 = "+999999"    ; Larger number with plus

        long result1 = conv.str2long(test_string1)
        long result2 = conv.str2long(test_string2)
        long result3 = conv.str2long(test_string3)
        long result4 = conv.str2long(test_string4)
        long result5 = conv.str2long(test_string5)
        long result6 = conv.str2long(test_string6)
        long result7 = conv.str2long(test_string7)
        long result8 = conv.str2long(test_string8)  ; This should return 0 for empty string
        long result9 = conv.str2long(test_string9) ; This should return 0 since it starts with non-digit
        long result10 = conv.str2long(test_string10)  ; Max unsigned byte
        long result11 = conv.str2long(test_string11)  ; Max signed byte
        long result12 = conv.str2long(test_string12)  ; Min signed byte
        long result13 = conv.str2long(test_string13)  ; Max unsigned word
        long result14 = conv.str2long(test_string14)  ; Max signed word
        long result15 = conv.str2long(test_string15)  ; Min signed word
        long result16 = conv.str2long(test_string16)  ; Just over 65535/10
        long result17 = conv.str2long(test_string17)  ; Larger number
        long result18 = conv.str2long(test_string18)  ; Larger number with +

        ; Print results using the built-in textio library
        txt.print("str ")
        txt.print(test_string1)
        txt.print(" = ")
        txt.print_l(result1)
        txt.nl()

        txt.print("str ")
        txt.print(test_string2)
        txt.print(" = ")
        txt.print_l(result2)
        txt.nl()

        txt.print("str ")
        txt.print(test_string3)
        txt.print(" = ")
        txt.print_l(result3)
        txt.nl()

        txt.print("str ")
        txt.print(test_string4)
        txt.print(" = ")
        txt.print_l(result4)
        txt.nl()

        txt.print("str ")
        txt.print(test_string5)
        txt.print(" = ")
        txt.print_l(result5)
        txt.nl()

        txt.print("str ")
        txt.print(test_string6)
        txt.print(" = ")
        txt.print_l(result6)
        txt.nl()

        txt.print("str ")
        txt.print(test_string7)
        txt.print(" = ")
        txt.print_l(result7)
        txt.nl()

        txt.print("str ")
        txt.print(test_string8)
        txt.print(" = ")
        txt.print_l(result8)
        txt.nl()

        txt.print("str ")
        txt.print(test_string9)
        txt.print(" = ")
        txt.print_l(result9)
        txt.nl()

        txt.print("str ")
        txt.print(test_string10)
        txt.print(" = ")
        txt.print_l(result10)
        txt.nl()

        txt.print("str ")
        txt.print(test_string11)
        txt.print(" = ")
        txt.print_l(result11)
        txt.nl()

        txt.print("str ")
        txt.print(test_string12)
        txt.print(" = ")
        txt.print_l(result12)
        txt.nl()

        txt.print("str ")
        txt.print(test_string13)
        txt.print(" = ")
        txt.print_l(result13)
        txt.nl()

        txt.print("str ")
        txt.print(test_string14)
        txt.print(" = ")
        txt.print_l(result14)
        txt.nl()

        txt.print("str ")
        txt.print(test_string15)
        txt.print(" = ")
        txt.print_l(result15)
        txt.nl()

        txt.print("str ")
        txt.print(test_string16)
        txt.print(" = ")
        txt.print_l(result16)
        txt.nl()

        txt.print("str ")
        txt.print(test_string17)
        txt.print(" = ")
        txt.print_l(result17)
        txt.nl()

        txt.print("str ")
        txt.print(test_string18)
        txt.print(" = ")
        txt.print_l(result18)
        txt.nl()

        ; To make sure the program finishes properly
        return
    }
}
