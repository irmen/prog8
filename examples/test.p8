%import textio
%zeropage basicsafe
main {
  bool[1] expected = [ true ]

  sub get() -> bool {
    txt.print("get() called. ")
    return true
  }

  sub start() {
    if get() == expected[0]
      txt.print("ok\n")
    else
      txt.print("fail\n")

    ; this is working fine:
    if expected[0] == get()
      txt.print("ok\n")
    else
      txt.print("fail\n")
  }
}
