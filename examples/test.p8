%import textio
%import floats
%zeropage basicsafe

main {
  sub start() {
    uword xx= $2000
    ubyte yy=$30
    ubyte zz=9
    sys.memset(xx+200, yy*2, ~yy)

    @($c030) = 10
    @(~xx) *= 2
    txt.print_ub(@($c030))

    float f1 = 1111.11
    float f2 = 2222.22
    float[] fa = [2222.22, 3333.33]

    swap(f1, fa[1])
    floats.print_f(f1)
    txt.nl()
    floats.print_f(fa[1])

  }
}
