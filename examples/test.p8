%import textio
%zeropage basicsafe


main {
  ubyte key

  sub pushing_fire() -> ubyte {
    return key == 'z'
  }

  sub pushing_left() -> ubyte {
    return key == 'k' or key == 157
  }

  sub pushing_right() -> ubyte {
    return key == 'l' or key == 29
  }

    sub start() {
        void pushing_fire()
        void pushing_left()
        void pushing_right()

        ubyte rnr = $99
        ubyte wordNr = ((rnr >= $33) as ubyte) +
            ((rnr >= $66) as ubyte) +
            ((rnr >= $99) as ubyte) +
            ((rnr >= $CC) as ubyte)

        ubyte wordNr2 = (rnr >= $33) as ubyte + (rnr >= $66) as ubyte + (rnr >= $99) as ubyte + (rnr >= $CC) as ubyte

        txt.print_uw(wordNr)
        txt.nl()
        txt.print_uw(wordNr2)
        txt.nl()
    }
}
