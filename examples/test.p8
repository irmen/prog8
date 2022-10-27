%import textio
%import floats
%zeropage basicsafe

main {
  float[10] flt

  sub start() {
    float ff = 9.0
    flt[1] = 42.42
    flt[1] = -9.0
    flt[1] = -ff
    flt[1] = -flt[1]            ; TODO also fix crash when selecting vm target: fpReg1 out of bounds
    floats.print_f(flt[1])
    txt.nl()
  }
}
