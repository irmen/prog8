%import textio
%import floats
%import string
%zeropage basicsafe

main {

    str name1 = "abc"
    str name2 = "irmen"
    ubyte[] arr1 = [11,22,0,33]
    uword[] arr2 = [1111,2222,0,3333]

  sub start() {
   sys.exit(42)
    floats.rndseedf(11,22,33)
    floats.print_f(floats.rndf())
    txt.nl()
    floats.print_f(floats.rndf())
    txt.nl()
    floats.print_f(floats.rndf())
    txt.nl()

    ubyte @shared xx
    ubyte value = 33
    uword value2 = 3333
    txt.print_ub(all(arr1))
    txt.print_ub(any(arr1))
    reverse(arr1)
    sort(arr1)
    txt.print_ub(value in name1)
    txt.print_ub('c' in name1)
    txt.print_ub(value in arr1)
    txt.print_ub(value2 in arr2)
    txt.print_ub(name1 == name2)
    txt.print_ub(name1 < name2)
    txt.print_ub(name1 >= name2)

  }
}
