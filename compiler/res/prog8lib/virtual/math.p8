; Internal Math library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

math {

    sub sin8u(ubyte angle) -> ubyte {
        ubyte[256] sintab = [$80, $83, $86, $89, $8c, $8f, $92, $95, $98, $9b, $9e, $a2, $a5, $a7, $aa, $ad, $b0, $b3, $b6, $b9,
                             $bc, $be, $c1, $c4, $c6, $c9, $cb, $ce, $d0, $d3, $d5, $d7, $da, $dc, $de, $e0,
                             $e2, $e4, $e6, $e8, $ea, $eb, $ed, $ee, $f0, $f1, $f3, $f4, $f5, $f6, $f8, $f9,
                             $fa, $fa, $fb, $fc, $fd, $fd, $fe, $fe, $fe, $ff, $ff, $ff, $ff, $ff, $ff, $ff,
                             $fe, $fe, $fe, $fd, $fd, $fc, $fb, $fa, $fa, $f9, $f8, $f6, $f5, $f4, $f3, $f1,
                             $f0, $ee, $ed, $eb, $ea, $e8, $e6, $e4, $e2, $e0, $de, $dc, $da, $d7, $d5, $d3,
                             $d0, $ce, $cb, $c9, $c6, $c4, $c1, $be, $bc, $b9, $b6, $b3, $b0, $ad, $aa, $a7,
                             $a5, $a2, $9e, $9b, $98, $95, $92, $8f, $8c, $89, $86, $83, $80, $7c, $79, $76,
                             $73, $70, $6d, $6a, $67, $64, $61, $5d, $5a, $58, $55, $52, $4f, $4c, $49, $46,
                             $43, $41, $3e, $3b, $39, $36, $34, $31, $2f, $2c, $2a, $28, $25, $23, $21, $1f,
                             $1d, $1b, $19, $17, $15, $14, $12, $11, $0f, $0e, $0c, $0b, $0a, $09, $07, $06,
                             $05, $05, $04, $03, $02, $02, $01, $01, $01, $00, $00, $00, $00, $00, $00, $00,
                             $01, $01, $01, $02, $02, $03, $04, $05, $05, $06, $07, $09, $0a, $0b, $0c, $0e,
                             $0f, $11, $12, $14, $15, $17, $19, $1b, $1d, $1f, $21, $23, $25, $28, $2a, $2c,
                             $2f, $31, $34, $36, $39, $3b, $3e, $41, $43, $46, $49, $4c, $4f, $52, $55, $58,
                             $5a, $5d, $61, $64, $67, $6a, $6d, $70, $73, $76, $79, $7c]
       return sintab[angle]
    }

    sub cos8u(ubyte angle) -> ubyte {
        ubyte[256] costab = [$ff, $ff, $ff, $ff,
                             $fe, $fe, $fe, $fd, $fd, $fc, $fb, $fa, $fa, $f9, $f8, $f6, $f5, $f4, $f3, $f1,
                             $f0, $ee, $ed, $eb, $ea, $e8, $e6, $e4, $e2, $e0, $de, $dc, $da, $d7, $d5, $d3,
                             $d0, $ce, $cb, $c9, $c6, $c4, $c1, $be, $bc, $b9, $b6, $b3, $b0, $ad, $aa, $a7,
                             $a5, $a2, $9e, $9b, $98, $95, $92, $8f, $8c, $89, $86, $83, $80, $7c, $79, $76,
                             $73, $70, $6d, $6a, $67, $64, $61, $5d, $5a, $58, $55, $52, $4f, $4c, $49, $46,
                             $43, $41, $3e, $3b, $39, $36, $34, $31, $2f, $2c, $2a, $28, $25, $23, $21, $1f,
                             $1d, $1b, $19, $17, $15, $14, $12, $11, $0f, $0e, $0c, $0b, $0a, $09, $07, $06,
                             $05, $05, $04, $03, $02, $02, $01, $01, $01, $00, $00, $00, $00, $00, $00, $00,
                             $01, $01, $01, $02, $02, $03, $04, $05, $05, $06, $07, $09, $0a, $0b, $0c, $0e,
                             $0f, $11, $12, $14, $15, $17, $19, $1b, $1d, $1f, $21, $23, $25, $28, $2a, $2c,
                             $2f, $31, $34, $36, $39, $3b, $3e, $41, $43, $46, $49, $4c, $4f, $52, $55, $58,
                             $5a, $5d, $61, $64, $67, $6a, $6d, $70, $73, $76, $79, $7c, $7f, $83, $86, $89,
                             $8c, $8f, $92, $95, $98, $9b, $9e, $a2, $a5, $a7, $aa, $ad, $b0, $b3, $b6, $b9,
                             $bc, $be, $c1, $c4, $c6, $c9, $cb, $ce, $d0, $d3, $d5, $d7, $da, $dc, $de, $e0,
                             $e2, $e4, $e6, $e8, $ea, $eb, $ed, $ee, $f0, $f1, $f3, $f4, $f5, $f6, $f8, $f9,
                             $fa, $fa, $fb, $fc, $fd, $fd, $fe, $fe, $fe, $ff, $ff, $ff ]
       return costab[angle]
    }

    sub sin8(ubyte angle) -> byte {
        return 42     ; TODO
    }

    sub cos8(ubyte angle) -> byte {
        return 42     ; TODO
    }

    sub sin16(ubyte angle) -> word {
        return 4242     ; TODO
    }

    sub cos16(ubyte angle) -> word {
        return 4242     ; TODO
    }

    sub sin16u(ubyte angle) -> uword {
        return 4242     ; TODO
    }

    sub cos16u(ubyte angle) -> uword {
        return 4242     ; TODO
    }

    sub sinr8u(ubyte radians) -> ubyte {
        ubyte[180] sintab = [
            $80, $84, $88, $8d,
            $91, $96, $9a, $9e, $a3, $a7, $ab, $af, $b3, $b7, $bb, $bf, $c3, $c7, $ca, $ce,
            $d1, $d5, $d8, $db, $de, $e1, $e4, $e7, $e9, $ec, $ee, $f0, $f2, $f4, $f6, $f7,
            $f9, $fa, $fb, $fc, $fd, $fe, $fe, $ff, $ff, $ff, $ff, $ff, $fe, $fe, $fd, $fc,
            $fb, $fa, $f9, $f7, $f6, $f4, $f2, $f0, $ee, $ec, $e9, $e7, $e4, $e1, $de, $db,
            $d8, $d5, $d1, $ce, $ca, $c7, $c3, $bf, $bb, $b7, $b3, $af, $ab, $a7, $a3, $9e,
            $9a, $96, $91, $8d, $88, $84, $80, $7b, $77, $72, $6e, $69, $65, $61, $5c, $58,
            $54, $50, $4c, $48, $44, $40, $3c, $38, $35, $31, $2e, $2a, $27, $24, $21, $1e,
            $1b, $18, $16, $13, $11, $0f, $0d, $0b, $09, $08, $06, $05, $04, $03, $02, $01,
            $01, $00, $00, $00, $00, $00, $01, $01, $02, $03, $04, $05, $06, $08, $09, $0b,
            $0d, $0f, $11, $13, $16, $18, $1b, $1e, $21, $24, $27, $2a, $2e, $31, $35, $38,
            $3c, $40, $44, $48, $4c, $50, $54, $58, $5c, $61, $65, $69, $6e, $72, $77, $7b]
        return sintab[radians]
    }

    sub cosr8u(ubyte radians) -> ubyte {
        ubyte[180] costab = [
            $ff, $ff, $ff, $fe, $fe, $fd, $fc,
            $fb, $fa, $f9, $f7, $f6, $f4, $f2, $f0, $ee, $ec, $e9, $e7, $e4, $e1, $de, $db,
            $d8, $d5, $d1, $ce, $ca, $c7, $c3, $bf, $bb, $b7, $b3, $af, $ab, $a7, $a3, $9e,
            $9a, $96, $91, $8d, $88, $84, $80, $7b, $77, $72, $6e, $69, $65, $61, $5c, $58,
            $54, $50, $4c, $48, $44, $40, $3c, $38, $35, $31, $2e, $2a, $27, $24, $21, $1e,
            $1b, $18, $16, $13, $11, $0f, $0d, $0b, $09, $08, $06, $05, $04, $03, $02, $01,
            $01, $00, $00, $00, $00, $00, $01, $01, $02, $03, $04, $05, $06, $08, $09, $0b,
            $0d, $0f, $11, $13, $16, $18, $1b, $1e, $21, $24, $27, $2a, $2e, $31, $35, $38,
            $3c, $40, $44, $48, $4c, $50, $54, $58, $5c, $61, $65, $69, $6e, $72, $77, $7b,
            $7f, $84, $88, $8d, $91, $96, $9a, $9e, $a3, $a7, $ab, $af, $b3, $b7, $bb, $bf,
            $c3, $c7, $ca, $ce, $d1, $d5, $d8, $db, $de, $e1, $e4, $e7, $e9, $ec, $ee, $f0,
            $f2, $f4, $f6, $f7, $f9, $fa, $fb, $fc, $fd, $fe, $fe, $ff, $ff ]
        return costab[radians]
    }

    sub sinr8(ubyte radians) -> byte {
        return 42     ; TODO
    }

    sub cosr8(ubyte radians) -> byte {
        return 42     ; TODO
    }

    sub sinr16(ubyte radians) -> word {
        return 4242     ; TODO
    }

    sub cosr16(ubyte radians) -> word {
        return 4242     ; TODO
    }

    sub sinr16u(ubyte radians) -> uword {
        return 4242     ; TODO
    }

    sub cosr16u(ubyte radians) -> uword {
        return 4242     ; TODO
    }
}
