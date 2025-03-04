%import textio
%zeropage basicsafe

main {
  sub start() {
    defer txt.print("defer\n")
    txt.print("1\n")
    label()
    txt.print("2\n")
label:
    txt.print("3\n")
  }
}
