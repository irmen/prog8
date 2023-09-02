%import textio
%import floats
%zeropage dontuse

main {
    sub start()  {
        float value1 = -0.8
        float value2 = 0.3
        float two = 2.0

        float result = value1*two + value2*two  ; TODO FIX: invalid result on c64, ok when the *two is removed or expression is split (it's not caused by pushFAC1/popFAC1)
        floats.print_f(result)
        txt.nl()
        txt.print("-1 was expected\n\n")       ; on C64: -1.1 is printed :(

        result = value2*two + value1*two        ; swapped operands around, now it's suddenly fine on C64...
        floats.print_f(result)
        txt.nl()
        txt.print("-1 was expected\n\n")       ; on C64: correct value is printed


        value1 = 0.8
        value2 = 0.3
        result = value1*two + value2*two
        floats.print_f(result)
        txt.nl()
        txt.print("2.2 was expected\n\n")       ; on C64: correct value is printed

        repeat {
        }
    }
}
