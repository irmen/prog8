%import floats
%import textio
%zeropage basicsafe

main {
  byte[10] foo
  ubyte[10] foou
  word[10] foow
  uword[10] foowu
  float[10] flt
  byte d

  sub start() {
    ; foo[0] = -d         ; TODO fix codegen in assignExpression that splits this up

    uword pointer = $1000
    uword index
    foou[1] = 42
    pointer[$40] = 24
    pointer[$40] = foou[1]+10
    txt.print_ub(@($1040))
    txt.nl()
    pointer[index+$100] = foou[1]
    pointer[index+$1000] = foou[1]+1
    txt.print_ub(@($1100))
    txt.nl()
    txt.print_ub(@($2000))
    txt.nl()


;    foo[0] = -foo[0]
;    foou[0] = ~foou[0]
;    foow[0] = -foow[0]
;    foowu[0] = ~foowu[0]
;    flt[0] = -flt[0]            ; TODO also fix crash when selecting vm target: fpReg1 out of bounds
  }
}
