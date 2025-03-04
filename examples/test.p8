%import textio
%zeropage basicsafe

main {
  sub start() {
    txt.print("1\n")
    label()
    txt.print("2\n")
label:
    txt.print("3\n")
  }
}
