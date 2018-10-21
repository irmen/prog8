%import c64utils
%output prg
%launcher basic

~ main  {

sub start() {

    c64.VMCSB |= 2		; activate lowercase charset

    ; greeting
    c64scr.print_string("Enter your name: ")

    return


}

}
