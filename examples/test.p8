%import floats
%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        &uword[30] wb = $2000
        &uword[100] array1 = $9e00
        &uword[30] array2 = &array1[len(wb)]

        txt.print_uwhex(&array1, true)           ; $9e00
        txt.print_uwhex(&array1[len(wb)], true)  ; $9e3c
        txt.print_uwhex(&array2, true)           ; $9e3c
    }
}
