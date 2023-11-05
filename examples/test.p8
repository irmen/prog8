%zeropage basicsafe
%option no_sysinit
%import textio

main {
  const ubyte FOO = 0
  const ubyte BAR = 1

  sub start() {
    when FOO+BAR {
        1-> txt.print("path 1")
        2-> txt.print("path 2")
        else-> txt.print("path 3")
    }
    txt.nl()
  }
}
