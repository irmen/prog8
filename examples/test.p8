%import textio
%import string
%zeropage basicsafe

main {

    str name1 = "abc"
    str name2 = "irmen"
    ubyte[] arr1 = [11,22,33]
    uword[] arr2 = [1111,2222,3333]

  sub start() {
    ubyte @shared xx
    ubyte value = 33
    uword value2 = 3333
    txt.print_ub(value in name1)
    txt.print_ub('c' in name1)
    txt.print_ub(name1 == name2)
    txt.print_ub(name1 < name2)
    txt.print_ub(value in arr1)
    txt.print_ub(value2 in arr2)

  }
}
