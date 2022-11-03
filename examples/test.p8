%import floats
%import textio
%zeropage basicsafe

main {
  float[10] flt

  sub start() {
    flt[1] = 42.42
    flt[1] = 3-flt[1]
    floats.print_f(flt[1])
  }
}
