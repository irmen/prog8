%import textio
%zeropage dontuse

main {
  bool[1] expected = [ true ]
  uword[1] expectedw = [4242 ]

  sub get() -> bool {
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return true
  }
  sub same() -> bool {
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return true
  }

  sub getw() -> uword {
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return 4242
  }
  sub samew() -> uword {
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return 4242
  }
  sub one() -> ubyte {
    %asm {{
        stz  P8ZP_SCRATCH_W1
        stz  P8ZP_SCRATCH_W1+1
        stz  P8ZP_SCRATCH_W2
        stz  P8ZP_SCRATCH_W2+1
        stz  P8ZP_SCRATCH_REG
        stz  P8ZP_SCRATCH_B1
    }}
    return 1
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

    if getw() == expectedw[0]
      txt.print("ok\n")
    else
      txt.print("fail\n")

    ; this is working fine:
    if expectedw[0] == getw()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    ; unquals

    if get() != expected[0]
      txt.print("fail\n")
    else
      txt.print("ok\n")

    ; this is working fine:
    if expected[0] != get()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    if getw() != expectedw[0]
      txt.print("fail\n")
    else
      txt.print("ok\n")

    ; this is working fine:
    if expectedw[0] != getw()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    ;  now with 2 non-simple operands:
    if get() == same()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    ; this is working fine:
    if same() == get()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    if getw() == samew()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    ; this is working fine:
    if samew() == getw()
      txt.print("ok\n")
    else
      txt.print("fail\n")

    ; unquals

    if get() != same()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    ; this is working fine:
    if same() != get()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    if getw() != samew()
      txt.print("fail\n")
    else
      txt.print("ok\n")

    ; this is working fine:
    if samew() != getw()
      txt.print("fail\n")
    else
      txt.print("ok\n")


     ; pointer stuff
     uword ptr = $4000
     @(ptr+one()) = 42
     if @(ptr+one()) == 42
        txt.print("ok\n")
     else
        txt.print("fail\n")
  }
}
