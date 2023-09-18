main {
    uword[] pages = [ &page_credits.chars_1]

    sub start() {
        ; cx16.r0  = pages[0]       ; TODO fix IR compiler error undefined symbol pages
        uword @shared foo = pages[0]    ; TODO fix IR compiler error no chunk with label 'page_credits.chars_1'  (caused by optimizer)
    }

}

page_credits {
  ubyte[] chars_1 = [11]
  ; TODO fix IR compiler crash when this array is moved into main block itself
}
