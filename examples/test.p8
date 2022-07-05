%import textio
%zeropage basicsafe

main  {

    bool boolvalue1 = true
    bool boolvalue2 = false
    ubyte ubvalue1 = true
    ubyte ubvalue2 = false

    sub start() {
        if ubvalue1<44 or ubvalue1>99
            txt.print("0\n")

        if boolvalue1 or boolvalue2
            txt.print("1\n")

        if boolvalue1 and boolvalue2
            txt.print("2\n")

;        if ubvalue1 or ubvalu2
;            txt.print("3\n")
;
;        if ubvalue1 and ubvalu2
;            txt.print("4\n")
     }
}
