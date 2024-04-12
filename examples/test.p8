%import textio
%zeropage basicsafe

main {
  sub show_bug(byte a, byte b) {
      if (a >= 0) == (b > 0) {
          txt.print("bug!")
      } else {
          txt.print("no bug.")
      }
      txt.nl()
  }

  sub start() {
      show_bug(-1, 4)
  }
}
