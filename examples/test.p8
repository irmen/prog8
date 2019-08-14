%import c64lib
%import c64utils
%import c64flt
%zeropage dontuse

main {

    sub start() {

        uword uw
        ubyte ub

;        uw = uw>>0
;        uw = uw>>7
        ub = uw>>8 as ubyte
        A++
        uw = uw>>8
        A++
        uw = msb(uw)

;        uw <<= 1
;        uw >>= 1
;
;        ub <<= 1
;        ub >>= 1
;
;        uw *= 2
;        ub *= 2
    }


          asmsub aa(byte arg @ Y) clobbers() {
            %asm {{
            rts
            }}
          }

}
