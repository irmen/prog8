%import textio
%zeropage basicsafe

main {

    str name1 = "abc"
    str name2 = "irmen"
    ubyte v1
    ubyte v2

    sub func(ubyte value) {
        value++
    }

  sub start() {
    func(v1==v2)
    func(v1>v2)
    func(name1 == name2)
    func(name1 > name2)
  }
}
