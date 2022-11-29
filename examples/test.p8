%import textio
%zeropage dontuse


; base level code size: $279


main {
  bool[1] expected = [ true ]
  uword[1] expectedw = [4242 ]

  sub get() -> bool {
    ubyte xx
    xx = 1
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return xx
  }
  sub same() -> bool {
    ubyte xx
    xx = 1
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return xx
  }

  sub getw() -> uword {
    uword xx=4242
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return xx
  }
  sub samew() -> uword {
    uword xx=4242
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return xx
  }
  sub differentw() -> uword {
    uword xx=9999
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return xx
  }
  sub one() -> ubyte {
    ubyte xx=1
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return xx
  }

  sub start() {
    if getw() == samew()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    if samew() == getw()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    if getw() != samew()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    if samew() != getw()
      txt.print("fail\n")
    else
      txt.print("ok\n")


    if getw() == differentw()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    if differentw() == getw()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    if getw() != differentw()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    if differentw() != getw()
      txt.print("ok\n")
    else
      txt.print("fail\n")

  }
}
