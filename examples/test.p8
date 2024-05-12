%import textio
%zeropage basicsafe

main {
  sub start() {
    uword workFunc=$2000

    void = call(workFunc)
  }
}
