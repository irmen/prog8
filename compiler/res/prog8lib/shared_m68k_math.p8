; Internal Math library routines - always included by the compiler
; note: some functions you might expect here are builtin functions,
;       such as abs, sqrt, clamp, min, max for example.

math {
    %option merge, ignore_unused, no_symbol_prefixing

    ; Sine/cosine lookup tables, one per routine (not shared).
    ; The tables live at block scope so the inlined
    ; routines below all reference the same single copy. Tables that end up
    ; unused (no trig routine called) are removed automatically.
    private ubyte[256] sin8u_table = [$80, $83, $86, $89, $8c, $8f, $92, $95, $98, $9b, $9e, $a2, $a5, $a7, $aa, $ad,
                               $b0, $b3, $b6, $b9, $bc, $be, $c1, $c4, $c6, $c9, $cb, $ce, $d0, $d3, $d5, $d7,
                               $da, $dc, $de, $e0, $e2, $e4, $e6, $e8, $ea, $eb, $ed, $ee, $f0, $f1, $f3, $f4,
                               $f5, $f6, $f8, $f9, $fa, $fa, $fb, $fc, $fd, $fd, $fe, $fe, $fe, $ff, $ff, $ff,
                               $ff, $ff, $ff, $ff, $fe, $fe, $fe, $fd, $fd, $fc, $fb, $fa, $fa, $f9, $f8, $f6,
                               $f5, $f4, $f3, $f1, $f0, $ee, $ed, $eb, $ea, $e8, $e6, $e4, $e2, $e0, $de, $dc,
                               $da, $d7, $d5, $d3, $d0, $ce, $cb, $c9, $c6, $c4, $c1, $be, $bc, $b9, $b6, $b3,
                               $b0, $ad, $aa, $a7, $a5, $a2, $9e, $9b, $98, $95, $92, $8f, $8c, $89, $86, $83,
                               $80, $7c, $79, $76, $73, $70, $6d, $6a, $67, $64, $61, $5d, $5a, $58, $55, $52,
                               $4f, $4c, $49, $46, $43, $41, $3e, $3b, $39, $36, $34, $31, $2f, $2c, $2a, $28,
                               $25, $23, $21, $1f, $1d, $1b, $19, $17, $15, $14, $12, $11, $0f, $0e, $0c, $0b,
                               $0a, $09, $07, $06, $05, $05, $04, $03, $02, $02, $01, $01, $01, $00, $00, $00,
                               $00, $00, $00, $00, $01, $01, $01, $02, $02, $03, $04, $05, $05, $06, $07, $09,
                               $0a, $0b, $0c, $0e, $0f, $11, $12, $14, $15, $17, $19, $1b, $1d, $1f, $21, $23,
                               $25, $28, $2a, $2c, $2f, $31, $34, $36, $39, $3b, $3e, $41, $43, $46, $49, $4c,
                               $4f, $52, $55, $58, $5a, $5d, $61, $64, $67, $6a, $6d, $70, $73, $76, $79, $7c]
    private ubyte[256] cos8u_table = [$ff, $ff, $ff, $ff, $fe, $fe, $fe, $fd, $fd, $fc, $fb, $fa, $fa, $f9, $f8, $f6,
                               $f5, $f4, $f3, $f1, $f0, $ee, $ed, $eb, $ea, $e8, $e6, $e4, $e2, $e0, $de, $dc,
                               $da, $d7, $d5, $d3, $d0, $ce, $cb, $c9, $c6, $c4, $c1, $be, $bc, $b9, $b6, $b3,
                               $b0, $ad, $aa, $a7, $a5, $a2, $9e, $9b, $98, $95, $92, $8f, $8c, $89, $86, $83,
                               $80, $7c, $79, $76, $73, $70, $6d, $6a, $67, $64, $61, $5d, $5a, $58, $55, $52,
                               $4f, $4c, $49, $46, $43, $41, $3e, $3b, $39, $36, $34, $31, $2f, $2c, $2a, $28,
                               $25, $23, $21, $1f, $1d, $1b, $19, $17, $15, $14, $12, $11, $0f, $0e, $0c, $0b,
                               $0a, $09, $07, $06, $05, $05, $04, $03, $02, $02, $01, $01, $01, $00, $00, $00,
                               $00, $00, $00, $00, $01, $01, $01, $02, $02, $03, $04, $05, $05, $06, $07, $09,
                               $0a, $0b, $0c, $0e, $0f, $11, $12, $14, $15, $17, $19, $1b, $1d, $1f, $21, $23,
                               $25, $28, $2a, $2c, $2f, $31, $34, $36, $39, $3b, $3e, $41, $43, $46, $49, $4c,
                               $4f, $52, $55, $58, $5a, $5d, $61, $64, $67, $6a, $6d, $70, $73, $76, $79, $7c,
                               $7f, $83, $86, $89, $8c, $8f, $92, $95, $98, $9b, $9e, $a2, $a5, $a7, $aa, $ad,
                               $b0, $b3, $b6, $b9, $bc, $be, $c1, $c4, $c6, $c9, $cb, $ce, $d0, $d3, $d5, $d7,
                               $da, $dc, $de, $e0, $e2, $e4, $e6, $e8, $ea, $eb, $ed, $ee, $f0, $f1, $f3, $f4,
                               $f5, $f6, $f8, $f9, $fa, $fa, $fb, $fc, $fd, $fd, $fe, $fe, $fe, $ff, $ff, $ff]
    private ubyte[256] sin8_table = [$00, $03, $06, $09, $0c, $0f, $12, $15, $18, $1b, $1e, $21, $24, $27, $2a, $2d,
                               $30, $33, $36, $39, $3b, $3e, $41, $43, $46, $49, $4b, $4e, $50, $52, $55, $57,
                               $59, $5b, $5e, $60, $62, $64, $66, $67, $69, $6b, $6c, $6e, $70, $71, $72, $74,
                               $75, $76, $77, $78, $79, $7a, $7b, $7b, $7c, $7d, $7d, $7e, $7e, $7e, $7e, $7e,
                               $7f, $7e, $7e, $7e, $7e, $7e, $7d, $7d, $7c, $7b, $7b, $7a, $79, $78, $77, $76,
                               $75, $74, $72, $71, $70, $6e, $6c, $6b, $69, $67, $66, $64, $62, $60, $5e, $5b,
                               $59, $57, $55, $52, $50, $4e, $4b, $49, $46, $43, $41, $3e, $3b, $39, $36, $33,
                               $30, $2d, $2a, $27, $24, $21, $1e, $1b, $18, $15, $12, $0f, $0c, $09, $06, $03,
                               $00, $fd, $fa, $f7, $f4, $f1, $ee, $eb, $e8, $e5, $e2, $df, $dc, $d9, $d6, $d3,
                               $d0, $cd, $ca, $c7, $c5, $c2, $bf, $bd, $ba, $b7, $b5, $b2, $b0, $ae, $ab, $a9,
                               $a7, $a5, $a2, $a0, $9e, $9c, $9a, $99, $97, $95, $94, $92, $90, $8f, $8e, $8c,
                               $8b, $8a, $89, $88, $87, $86, $85, $85, $84, $83, $83, $82, $82, $82, $82, $82,
                               $81, $82, $82, $82, $82, $82, $83, $83, $84, $85, $85, $86, $87, $88, $89, $8a,
                               $8b, $8c, $8e, $8f, $90, $92, $94, $95, $97, $99, $9a, $9c, $9e, $a0, $a2, $a5,
                               $a7, $a9, $ab, $ae, $b0, $b2, $b5, $b7, $ba, $bd, $bf, $c2, $c5, $c7, $ca, $cd,
                               $d0, $d3, $d6, $d9, $dc, $df, $e2, $e5, $e8, $eb, $ee, $f1, $f4, $f7, $fa, $fd]
    private ubyte[256] cos8_table = [$7f, $7e, $7e, $7e, $7e, $7e, $7d, $7d, $7c, $7b, $7b, $7a, $79, $78, $77, $76,
                               $75, $74, $72, $71, $70, $6e, $6c, $6b, $69, $67, $66, $64, $62, $60, $5e, $5b,
                               $59, $57, $55, $52, $50, $4e, $4b, $49, $46, $43, $41, $3e, $3b, $39, $36, $33,
                               $30, $2d, $2a, $27, $24, $21, $1e, $1b, $18, $15, $12, $0f, $0c, $09, $06, $03,
                               $00, $fd, $fa, $f7, $f4, $f1, $ee, $eb, $e8, $e5, $e2, $df, $dc, $d9, $d6, $d3,
                               $d0, $cd, $ca, $c7, $c5, $c2, $bf, $bd, $ba, $b7, $b5, $b2, $b0, $ae, $ab, $a9,
                               $a7, $a5, $a2, $a0, $9e, $9c, $9a, $99, $97, $95, $94, $92, $90, $8f, $8e, $8c,
                               $8b, $8a, $89, $88, $87, $86, $85, $85, $84, $83, $83, $82, $82, $82, $82, $82,
                               $81, $82, $82, $82, $82, $82, $83, $83, $84, $85, $85, $86, $87, $88, $89, $8a,
                               $8b, $8c, $8e, $8f, $90, $92, $94, $95, $97, $99, $9a, $9c, $9e, $a0, $a2, $a5,
                               $a7, $a9, $ab, $ae, $b0, $b2, $b5, $b7, $ba, $bd, $bf, $c2, $c5, $c7, $ca, $cd,
                               $d0, $d3, $d6, $d9, $dc, $df, $e2, $e5, $e8, $eb, $ee, $f1, $f4, $f7, $fa, $fd,
                               $00, $03, $06, $09, $0c, $0f, $12, $15, $18, $1b, $1e, $21, $24, $27, $2a, $2d,
                               $30, $33, $36, $39, $3b, $3e, $41, $43, $46, $49, $4b, $4e, $50, $52, $55, $57,
                               $59, $5b, $5e, $60, $62, $64, $66, $67, $69, $6b, $6c, $6e, $70, $71, $72, $74,
                               $75, $76, $77, $78, $79, $7a, $7b, $7b, $7c, $7d, $7d, $7e, $7e, $7e, $7e, $7e]
    private ubyte[180] sinr8u_table = [$80, $84, $88, $8d, $91, $96, $9a, $9e, $a3, $a7, $ab, $af, $b3, $b7, $bb, $bf,
                               $c3, $c7, $ca, $ce, $d1, $d5, $d8, $db, $de, $e1, $e4, $e7, $e9, $ec, $ee, $f0,
                               $f2, $f4, $f6, $f7, $f9, $fa, $fb, $fc, $fd, $fe, $fe, $ff, $ff, $ff, $ff, $ff,
                               $fe, $fe, $fd, $fc, $fb, $fa, $f9, $f7, $f6, $f4, $f2, $f0, $ee, $ec, $e9, $e7,
                               $e4, $e1, $de, $db, $d8, $d5, $d1, $ce, $ca, $c7, $c3, $bf, $bb, $b7, $b3, $af,
                               $ab, $a7, $a3, $9e, $9a, $96, $91, $8d, $88, $84, $80, $7b, $77, $72, $6e, $69,
                               $65, $61, $5c, $58, $54, $50, $4c, $48, $44, $40, $3c, $38, $35, $31, $2e, $2a,
                               $27, $24, $21, $1e, $1b, $18, $16, $13, $11, $0f, $0d, $0b, $09, $08, $06, $05,
                               $04, $03, $02, $01, $01, $00, $00, $00, $00, $00, $01, $01, $02, $03, $04, $05,
                               $06, $08, $09, $0b, $0d, $0f, $11, $13, $16, $18, $1b, $1e, $21, $24, $27, $2a,
                               $2e, $31, $35, $38, $3c, $40, $44, $48, $4c, $50, $54, $58, $5c, $61, $65, $69,
                               $6e, $72, $77, $7b]
    private ubyte[180] cosr8u_table = [$ff, $ff, $ff, $fe, $fe, $fd, $fc, $fb, $fa, $f9, $f7, $f6, $f4, $f2, $f0, $ee,
                               $ec, $e9, $e7, $e4, $e1, $de, $db, $d8, $d5, $d1, $ce, $ca, $c7, $c3, $bf, $bb,
                               $b7, $b3, $af, $ab, $a7, $a3, $9e, $9a, $96, $91, $8d, $88, $84, $80, $7b, $77,
                               $72, $6e, $69, $65, $61, $5c, $58, $54, $50, $4c, $48, $44, $40, $3c, $38, $35,
                               $31, $2e, $2a, $27, $24, $21, $1e, $1b, $18, $16, $13, $11, $0f, $0d, $0b, $09,
                               $08, $06, $05, $04, $03, $02, $01, $01, $00, $00, $00, $00, $00, $01, $01, $02,
                               $03, $04, $05, $06, $08, $09, $0b, $0d, $0f, $11, $13, $16, $18, $1b, $1e, $21,
                               $24, $27, $2a, $2e, $31, $35, $38, $3c, $40, $44, $48, $4c, $50, $54, $58, $5c,
                               $61, $65, $69, $6e, $72, $77, $7b, $7f, $84, $88, $8d, $91, $96, $9a, $9e, $a3,
                               $a7, $ab, $af, $b3, $b7, $bb, $bf, $c3, $c7, $ca, $ce, $d1, $d5, $d8, $db, $de,
                               $e1, $e4, $e7, $e9, $ec, $ee, $f0, $f2, $f4, $f6, $f7, $f9, $fa, $fb, $fc, $fd,
                               $fe, $fe, $ff, $ff]
    private ubyte[180] sinr8_table = [$00, $04, $08, $0d, $11, $16, $1a, $1e, $23, $27, $2b, $2f, $33, $37, $3b, $3f,
                               $43, $47, $4a, $4e, $51, $54, $58, $5b, $5e, $61, $64, $66, $69, $6b, $6d, $70,
                               $72, $74, $75, $77, $78, $7a, $7b, $7c, $7d, $7d, $7e, $7e, $7e, $7f, $7e, $7e,
                               $7e, $7d, $7d, $7c, $7b, $7a, $78, $77, $75, $74, $72, $70, $6d, $6b, $69, $66,
                               $64, $61, $5e, $5b, $58, $54, $51, $4e, $4a, $47, $43, $3f, $3b, $37, $33, $2f,
                               $2b, $27, $23, $1e, $1a, $16, $11, $0d, $08, $04, $00, $fc, $f8, $f3, $ef, $ea,
                               $e6, $e2, $dd, $d9, $d5, $d1, $cd, $c9, $c5, $c1, $bd, $b9, $b6, $b2, $af, $ac,
                               $a8, $a5, $a2, $9f, $9c, $9a, $97, $95, $93, $90, $8e, $8c, $8b, $89, $88, $86,
                               $85, $84, $83, $83, $82, $82, $82, $81, $82, $82, $82, $83, $83, $84, $85, $86,
                               $88, $89, $8b, $8c, $8e, $90, $93, $95, $97, $9a, $9c, $9f, $a2, $a5, $a8, $ac,
                               $af, $b2, $b6, $b9, $bd, $c1, $c5, $c9, $cd, $d1, $d5, $d9, $dd, $e2, $e6, $ea,
                               $ef, $f3, $f8, $fc]
    private ubyte[180] cosr8_table = [$7f, $7e, $7e, $7e, $7d, $7d, $7c, $7b, $7a, $78, $77, $75, $74, $72, $70, $6d,
                               $6b, $69, $66, $64, $61, $5e, $5b, $58, $54, $51, $4e, $4a, $47, $43, $3f, $3b,
                               $37, $33, $2f, $2b, $27, $23, $1e, $1a, $16, $11, $0d, $08, $04, $00, $fc, $f8,
                               $f3, $ef, $ea, $e6, $e2, $dd, $d9, $d5, $d1, $cd, $c9, $c5, $c1, $bd, $b9, $b6,
                               $b2, $af, $ac, $a8, $a5, $a2, $9f, $9c, $9a, $97, $95, $93, $90, $8e, $8c, $8b,
                               $89, $88, $86, $85, $84, $83, $83, $82, $82, $82, $81, $82, $82, $82, $83, $83,
                               $84, $85, $86, $88, $89, $8b, $8c, $8e, $90, $93, $95, $97, $9a, $9c, $9f, $a2,
                               $a5, $a8, $ac, $af, $b2, $b6, $b9, $bd, $c1, $c5, $c9, $cd, $d1, $d5, $d9, $dd,
                               $e2, $e6, $ea, $ef, $f3, $f8, $fc, $00, $04, $08, $0d, $11, $16, $1a, $1e, $23,
                               $27, $2b, $2f, $33, $37, $3b, $3f, $43, $47, $4a, $4e, $51, $54, $58, $5b, $5e,
                               $61, $64, $66, $69, $6b, $6d, $70, $72, $74, $75, $77, $78, $7a, $7b, $7c, $7d,
                               $7d, $7e, $7e, $7e]

    inline sub sin8u(ubyte angle) -> ubyte {
        return math.sin8u_table[angle]
    }

    inline sub cos8u(ubyte angle) -> ubyte {
        return math.cos8u_table[angle]
    }

    inline sub sin8(ubyte angle) -> byte {
        return math.sin8_table[angle] as byte
    }

    inline sub cos8(ubyte angle) -> byte {
        return math.cos8_table[angle] as byte
    }

    inline sub sinr8u(ubyte radians) -> ubyte {
        return math.sinr8u_table[radians]
    }

    inline sub cosr8u(ubyte radians) -> ubyte {
        return math.cosr8u_table[radians]
    }

    inline sub sinr8(ubyte radians) -> byte {
        return math.sinr8_table[radians] as byte
    }

    inline sub cosr8(ubyte radians) -> byte {
        return math.cosr8_table[radians] as byte
    }

    ; "X ABC" pseudo random number generator
    ; Algorithm from codebase64.net, matching the 6502 math routines.
    ; State: 4 bytes (x1,c1,a1,b1). Default seed: $00c2, $1137.
    ; Very fast on 68000: only byte ops, no shifts > 1, no 32-bit multiply.

    private ubyte x1 = $00
    private ubyte c1 = $c2
    private ubyte a1 = $11
    private ubyte b1 = $37

    ; streaming CRC state (m68k has no cx16.rN scratch registers)
    private uword crc16_state = $0000
    private long crc32_state = $00000000

    sub rndw() -> uword {
        %option noframe
        %asm {{
            addq.b  #1,math.x1
            move.b  math.x1,d0
            move.b  math.c1,d1
            eor.b   d1,d0
            move.b  math.a1,d1
            eor.b   d1,d0
            move.b  d0,math.a1
            move.b  d0,d2
            move.b  math.b1,d1
            add.b   d1,d0
            move.b  d0,math.b1
            lsr.b   #1,d0
            eor.b   d2,d0
            move.b  math.c1,d1
            addx.b  d1,d0
            move.b  d0,math.c1
            clr.w   d0
            move.b  math.b1,d0
            lsl.w   #8,d0
            move.b  math.c1,d0
            rts
        }}
    }

    sub rnd() -> ubyte {
        return lsb(math.rndw())
    }

    sub rndseed(uword seed1, uword seed2) {
        %option noframe
        %asm {{
            move.b  math.rndseed.seed1+1,math.x1
            move.b  math.rndseed.seed1,math.c1
            move.b  math.rndseed.seed2+1,math.a1
            move.b  math.rndseed.seed2,math.b1
        }}
    }

    sub randrange(ubyte n) -> ubyte {
        ; -- return random number uniformly distributed from 0 to n-1
        return msb(math.rnd() * (n as uword))
    }

    sub randrangew(uword n) -> uword {
        %option noframe
        %asm {{
            addq.b  #1,math.x1
            move.b  math.x1,d0
            move.b  math.c1,d1
            eor.b   d1,d0
            move.b  math.a1,d1
            eor.b   d1,d0
            move.b  d0,math.a1
            move.b  d0,d2
            move.b  math.b1,d1
            add.b   d1,d0
            move.b  d0,math.b1
            lsr.b   #1,d0
            eor.b   d2,d0
            move.b  math.c1,d1
            addx.b  d1,d0
            move.b  d0,math.c1
            clr.w   d0
            move.b  math.b1,d0
            lsl.w   #8,d0
            move.b  math.c1,d0
            moveq   #0,d1
            move.w  math.randrangew.n,d1
            mulu.w  d1,d0
            swap    d0
            rts
        }}
    }

    asmsub log2(ubyte value @D0) -> ubyte @D0 {
        ; returns the integer base-2 logarithm of the unsigned byte value (position of the highest set bit)
        ; returns 0 for value = 0
        %asm {{
            move.b  d0,d1
            moveq   #7,d2
        .loop:
            btst    d2,d1
            bne     .found
            subq.b  #1,d2
            bcc     .loop
            moveq   #0,d0
            rts
        .found:
            move.b  d2,d0
            rts
        }}
    }

    asmsub log2w(uword value @D0) -> ubyte @D0 {
        ; returns the integer base-2 logarithm of the unsigned word value (position of the highest set bit)
        ; returns 0 for value = 0
        %asm {{
            move.w  d0,d1
            moveq   #15,d2
        .loop:
            btst    d2,d1
            bne     .found
            subq.b  #1,d2
            bcc     .loop
            moveq   #0,d0
            rts
        .found:
            move.b  d2,d0
            rts
        }}
    }

    asmsub log2l(long value @D0) -> ubyte @D0 {
        ; returns the integer base-2 logarithm of the signed long value (position of the highest set bit)
        ; returns 0 for value = 0
        %asm {{
            move.l  d0,d1
            moveq   #31,d2
        .loop:
            btst    d2,d1
            bne     .found
            subq.b  #1,d2
            bcc     .loop
            moveq   #0,d0
            rts
        .found:
            move.b  d2,d0
            rts
        }}
    }

    inline asmsub diff(ubyte v1 @D0, ubyte v2 @D1) -> ubyte @D0 {
        ; -- returns the (absolute) difference, or distance, between the two bytes
        %asm {{
            sub.b   d1,d0
            bcc     .done
            neg.b   d0
.done:      local
        }}
    }

    inline asmsub diffw(uword w1 @D0, uword w2 @D1) -> uword @D0 {
        ; -- returns the (absolute) difference, or distance, between the two words
        %asm {{
            sub.w   d1,d0
            bcc     .done
            neg.w   d0
.done:      local
        }}
    }

    inline asmsub diffl(long l1 @D0, long l2 @D1) -> long @D0 {
        ; -- returns the (absolute) difference, or distance, between the two longs
        %asm {{
            sub.l   d1,d0
            bcc     .done
            neg.l   d0
.done:      local
        }}
    }

    asmsub lerp(ubyte v0 @D0, ubyte v1 @D1, ubyte t @D2) -> ubyte @D0 {
        ; Linear interpolation (LERP)
        ; returns an interpolation between two inputs (v0, v1) for a parameter t in the interval [0, 255]
        ; guarantees v = v1 when t = 255
        %asm {{
            move.b  d0,d3           ; save v0
            cmp.b   d1,d0
            bcc     .descending
            ; ascending: v1 > v0
            sub.b   d0,d1           ; d1 = delta = v1 - v0
            move.b  d2,d0           ; d0 = t
            and.w   #$ff,d0
            and.w   #$ff,d1
            mulu.w  d1,d0           ; d0 = t * delta
            add.w   #255,d0         ; round up
            lsr.w   #8,d0           ; d0 = upper byte
            add.b   d3,d0           ; result = v0 + upper
            rts
        .descending:
            sub.b   d1,d0           ; d0 = delta = v0 - v1
            move.b  d2,d1           ; d1 = t
            and.w   #$ff,d0
            and.w   #$ff,d1
            mulu.w  d0,d1           ; d1 = t * delta
            add.w   #255,d1         ; round up
            lsr.w   #8,d1           ; d1 = upper byte
            move.b  d3,d0           ; d0 = v0
            sub.b   d1,d0           ; result = v0 - upper
            rts
        }}
    }

    asmsub lerpw(uword v0 @D0, uword v1 @D1, uword t @D2) -> uword @D0 {
        ; Linear interpolation (LERP) on word values
        ; returns an interpolation between two inputs (v0, v1) for a parameter t in the interval [0, 65535]
        ; guarantees v = v1 when t = 65535
        ; the 6502 version uses mul16_last_upper() for the high half of t*delta; here the
        ; full 32-bit unsigned product is computed instead, giving the same ceil(product/65536) result.
        %asm {{
            move.w  d0,d3           ; save v0
            cmp.w   d1,d0
            bcc     .descending
            ; ascending: v1 > v0
            sub.w   d0,d1           ; d1 = delta = v1 - v0
            move.w  d2,d0           ; d0 = t
            mulu.w  d1,d0           ; d0 = t * delta (32-bit)
            move.w  d0,d1           ; d1 = low word
            clr.w   d0              ; d0 = high word << 16
            swap    d0              ; d0 = high word
            tst.w   d1
            beq     .done_asc
            addq.w  #1,d0           ; ceil(product / 65536)
        .done_asc:
            add.w   d3,d0           ; result = v0 + upper
            rts
        .descending:
            sub.w   d1,d0           ; d0 = delta = v0 - v1
            move.w  d2,d1           ; d1 = t
            mulu.w  d0,d1           ; d1 = t * delta (32-bit)
            move.w  d1,d0           ; d0 = low word
            clr.w   d1              ; d1 = high word << 16
            swap    d1              ; d1 = high word
            tst.w   d0
            beq     .done_desc
            addq.w  #1,d1           ; ceil(product / 65536)
        .done_desc:
            move.w  d3,d0           ; d0 = v0
            sub.w   d1,d0           ; result = v0 - upper
            rts
        }}
    }

    asmsub interpolate(ubyte v @D0, ubyte inputMin @D1, ubyte inputMax @D2, ubyte outputMin @D3, ubyte outputMax @D4) -> ubyte @D0 {
        ; Interpolate a value v in interval [inputMin, inputMax] to output interval [outputMin, outputMax]
        %asm {{
            sub.b   d3,d4           ; d4 = delta_out = outputMax - outputMin (byte)
            sub.b   d1,d0           ; d0 = diff_in = v - inputMin
            and.w   #$ff,d0
            lsl.w   #8,d0           ; d0 = diff_in * 256
            add.b   d2,d0           ; d0 = diff_in * 256 + inputMax (numerator)
            sub.b   d1,d2           ; d2 = denom = inputMax - inputMin
            and.w   #$ff,d2
            and.l   #$ffff,d0       ; clear high word for divu
            divu.w  d2,d0           ; d0 = [remainder:quotient]
            and.w   #$ff,d4
            mulu.w  d4,d0           ; d0 = tmp * delta_out (32-bit)
            lsr.l   #8,d0           ; d0 = high byte of product
            add.b   d3,d0           ; result = msb + outputMin
            rts
        }}
    }

    asmsub interpolatew(uword v @D0, uword inputMin @D1, uword inputMax @D2, uword outputMin @D3, uword outputMax @D4) -> uword @D0 {
        ; Interpolate a value v in interval [inputMin, inputMax] to output interval [outputMin, outputMax]
        ; Uses a 32-bit intermediate product, so it works best when v is within the input range.
        %asm {{
            sub.w   d3,d4           ; d4 = delta_out = outputMax - outputMin
            sub.w   d1,d0           ; d0 = diff_in = v - inputMin
            mulu.w  d4,d0           ; d0 = diff_in * delta_out (32-bit)
            sub.w   d1,d2           ; d2 = denom = inputMax - inputMin
            divu.w  d2,d0           ; d0 = [remainder:quotient]
            add.w   d3,d0           ; result = outputMin + quotient
            rts
        }}
    }

    asmsub gcd(uword a @D0, uword b @D1) -> uword @D0 {
        ; Calculate the Greatest Common Divisor of two 16-bit unsigned integers using the Binary GCD algorithm (Stein's algorithm).
        %asm {{
            tst.w   d0
            beq     .done_b
            tst.w   d1
            beq     .done_a

            moveq   #0,d2           ; shift = 0

.shift_loop
            move.w  d0,d3
            or.w    d1,d3
            btst    #0,d3
            bne     .shift_done
            lsr.w   #1,d0
            lsr.w   #1,d1
            addq.w  #1,d2
            bra     .shift_loop

.shift_done
            ; remove remaining factors of 2 from a
.a_loop
            btst    #0,d0
            bne     .a_done
            lsr.w   #1,d0
            bra     .a_loop

.a_done
            ; loop while b != 0
.b_outer
            tst.w   d1
            beq     .finish

.b_inner
            btst    #0,d1
            bne     .b_done
            lsr.w   #1,d1
            bra     .b_inner

.b_done
            cmp.w   d1,d0
            bls     .no_swap        ; if a <= b, no swap
            exg     d0,d1           ; swap a and b
.no_swap
            sub.w   d0,d1           ; b = b - a
            bra     .b_outer

.finish
            lsl.w   d2,d0           ; a << shift
.done_a
            rts

.done_b
            move.w  d1,d0
            rts
        }}
    }

    %asm {{
        SECTION .text,code
        ALIGN 2
math.crc16_table:
        dc.w    $0000, $1021, $2042, $3063, $4084, $50A5, $60C6, $70E7
        dc.w    $8108, $9129, $A14A, $B16B, $C18C, $D1AD, $E1CE, $F1EF
        dc.w    $1231, $0210, $3273, $2252, $52B5, $4294, $72F7, $62D6
        dc.w    $9339, $8318, $B37B, $A35A, $D3BD, $C39C, $F3FF, $E3DE
        dc.w    $2462, $3443, $0420, $1401, $64E6, $74C7, $44A4, $5485
        dc.w    $A56A, $B54B, $8528, $9509, $E5EE, $F5CF, $C5AC, $D58D
        dc.w    $3653, $2672, $1611, $0630, $76D7, $66F6, $5695, $46B4
        dc.w    $B75B, $A77A, $9719, $8738, $F7DF, $E7FE, $D79D, $C7BC
        dc.w    $48C4, $58E5, $6886, $78A7, $0840, $1861, $2802, $3823
        dc.w    $C9CC, $D9ED, $E98E, $F9AF, $8948, $9969, $A90A, $B92B
        dc.w    $5AF5, $4AD4, $7AB7, $6A96, $1A71, $0A50, $3A33, $2A12
        dc.w    $DBFD, $CBDC, $FBBF, $EB9E, $9B79, $8B58, $BB3B, $AB1A
        dc.w    $6CA6, $7C87, $4CE4, $5CC5, $2C22, $3C03, $0C60, $1C41
        dc.w    $EDAE, $FD8F, $CDEC, $DDCD, $AD2A, $BD0B, $8D68, $9D49
        dc.w    $7E97, $6EB6, $5ED5, $4EF4, $3E13, $2E32, $1E51, $0E70
        dc.w    $FF9F, $EFBE, $DFDD, $CFFC, $BF1B, $AF3A, $9F59, $8F78
        dc.w    $9188, $81A9, $B1CA, $A1EB, $D10C, $C12D, $F14E, $E16F
        dc.w    $1080, $00A1, $30C2, $20E3, $5004, $4025, $7046, $6067
        dc.w    $83B9, $9398, $A3FB, $B3DA, $C33D, $D31C, $E37F, $F35E
        dc.w    $02B1, $1290, $22F3, $32D2, $4235, $5214, $6277, $7256
        dc.w    $B5EA, $A5CB, $95A8, $8589, $F56E, $E54F, $D52C, $C50D
        dc.w    $34E2, $24C3, $14A0, $0481, $7466, $6447, $5424, $4405
        dc.w    $A7DB, $B7FA, $8799, $97B8, $E75F, $F77E, $C71D, $D73C
        dc.w    $26D3, $36F2, $0691, $16B0, $6657, $7676, $4615, $5634
        dc.w    $D94C, $C96D, $F90E, $E92F, $99C8, $89E9, $B98A, $A9AB
        dc.w    $5844, $4865, $7806, $6827, $18C0, $08E1, $3882, $28A3
        dc.w    $CB7D, $DB5C, $EB3F, $FB1E, $8BF9, $9BD8, $ABBB, $BB9A
        dc.w    $4A75, $5A54, $6A37, $7A16, $0AF1, $1AD0, $2AB3, $3A92
        dc.w    $FD2E, $ED0F, $DD6C, $CD4D, $BDAA, $AD8B, $9DE8, $8DC9
        dc.w    $7C26, $6C07, $5C64, $4C45, $3CA2, $2C83, $1CE0, $0CC1
        dc.w    $EF1F, $FF3E, $CF5D, $DF7C, $AF9B, $BFBA, $8FD9, $9FF8
        dc.w    $6E17, $7E36, $4E55, $5E74, $2E93, $3EB2, $0ED1, $1EF0

        ALIGN 2
math.crc32_table:
        dc.l    $00000000, $77073096, $EE0E612C, $990951BA
        dc.l    $076DC419, $706AF48F, $E963A535, $9E6495A3
        dc.l    $0EDB8832, $79DCB8A4, $E0D5E91E, $97D2D988
        dc.l    $09B64C2B, $7EB17CBD, $E7B82D07, $90BF1D91
        dc.l    $1DB71064, $6AB020F2, $F3B97148, $84BE41DE
        dc.l    $1ADAD47D, $6DDDE4EB, $F4D4B551, $83D385C7
        dc.l    $136C9856, $646BA8C0, $FD62F97A, $8A65C9EC
        dc.l    $14015C4F, $63066CD9, $FA0F3D63, $8D080DF5
        dc.l    $3B6E20C8, $4C69105E, $D56041E4, $A2677172
        dc.l    $3C03E4D1, $4B04D447, $D20D85FD, $A50AB56B
        dc.l    $35B5A8FA, $42B2986C, $DBBBC9D6, $ACBCF940
        dc.l    $32D86CE3, $45DF5C75, $DCD60DCF, $ABD13D59
        dc.l    $26D930AC, $51DE003A, $C8D75180, $BFD06116
        dc.l    $21B4F4B5, $56B3C423, $CFBA9599, $B8BDA50F
        dc.l    $2802B89E, $5F058808, $C60CD9B2, $B10BE924
        dc.l    $2F6F7C87, $58684C11, $C1611DAB, $B6662D3D
        dc.l    $76DC4190, $01DB7106, $98D220BC, $EFD5102A
        dc.l    $71B18589, $06B6B51F, $9FBFE4A5, $E8B8D433
        dc.l    $7807C9A2, $0F00F934, $9609A88E, $E10E9818
        dc.l    $7F6A0DBB, $086D3D2D, $91646C97, $E6635C01
        dc.l    $6B6B51F4, $1C6C6162, $856530D8, $F262004E
        dc.l    $6C0695ED, $1B01A57B, $8208F4C1, $F50FC457
        dc.l    $65B0D9C6, $12B7E950, $8BBEB8EA, $FCB9887C
        dc.l    $62DD1DDF, $15DA2D49, $8CD37CF3, $FBD44C65
        dc.l    $4DB26158, $3AB551CE, $A3BC0074, $D4BB30E2
        dc.l    $4ADFA541, $3DD895D7, $A4D1C46D, $D3D6F4FB
        dc.l    $4369E96A, $346ED9FC, $AD678846, $DA60B8D0
        dc.l    $44042D73, $33031DE5, $AA0A4C5F, $DD0D7CC9
        dc.l    $5005713C, $270241AA, $BE0B1010, $C90C2086
        dc.l    $5768B525, $206F85B3, $B966D409, $CE61E49F
        dc.l    $5EDEF90E, $29D9C998, $B0D09822, $C7D7A8B4
        dc.l    $59B33D17, $2EB40D81, $B7BD5C3B, $C0BA6CAD
        dc.l    $EDB88320, $9ABFB3B6, $03B6E20C, $74B1D29A
        dc.l    $EAD54739, $9DD277AF, $04DB2615, $73DC1683
        dc.l    $E3630B12, $94643B84, $0D6D6A3E, $7A6A5AA8
        dc.l    $E40ECF0B, $9309FF9D, $0A00AE27, $7D079EB1
        dc.l    $F00F9344, $8708A3D2, $1E01F268, $6906C2FE
        dc.l    $F762575D, $806567CB, $196C3671, $6E6B06E7
        dc.l    $FED41B76, $89D32BE0, $10DA7A5A, $67DD4ACC
        dc.l    $F9B9DF6F, $8EBEEFF9, $17B7BE43, $60B08ED5
        dc.l    $D6D6A3E8, $A1D1937E, $38D8C2C4, $4FDFF252
        dc.l    $D1BB67F1, $A6BC5767, $3FB506DD, $48B2364B
        dc.l    $D80D2BDA, $AF0A1B4C, $36034AF6, $41047A60
        dc.l    $DF60EFC3, $A867DF55, $316E8EEF, $4669BE79
        dc.l    $CB61B38C, $BC66831A, $256FD2A0, $5268E236
        dc.l    $CC0C7795, $BB0B4703, $220216B9, $5505262F
        dc.l    $C5BA3BBE, $B2BD0B28, $2BB45A92, $5CB36A04
        dc.l    $C2D7FFA7, $B5D0CF31, $2CD99E8B, $5BDEAE1D
        dc.l    $9B64C2B0, $EC63F226, $756AA39C, $026D930A
        dc.l    $9C0906A9, $EB0E363F, $72076785, $05005713
        dc.l    $95BF4A82, $E2B87A14, $7BB12BAE, $0CB61B38
        dc.l    $92D28E9B, $E5D5BE0D, $7CDCEFB7, $0BDBDF21
        dc.l    $86D3D2D4, $F1D4E242, $68DDB3F8, $1FDA836E
        dc.l    $81BE16CD, $F6B9265B, $6FB077E1, $18B74777
        dc.l    $88085AE6, $FF0F6A70, $66063BCA, $11010B5C
        dc.l    $8F659EFF, $F862AE69, $616BFFD3, $166CCF45
        dc.l    $A00AE278, $D70DD2EE, $4E048354, $3903B3C2
        dc.l    $A7672661, $D06016F7, $4969474D, $3E6E77DB
        dc.l    $AED16A4A, $D9D65ADC, $40DF0B66, $37D83BF0
        dc.l    $A9BCAE53, $DEBB9EC5, $47B2CF7F, $30B5FFE9
        dc.l    $BDBDF21C, $CABAC28A, $53B39330, $24B4A3A6
        dc.l    $BAD03605, $CDD70693, $54DE5729, $23D967BF
        dc.l    $B3667A2E, $C4614AB8, $5D681B02, $2A6F2B94
        dc.l    $B40BBE37, $C30C8EA1, $5A05DF1B, $2D02EF8D
    }}

    asmsub crc16(^^ubyte data @A0, uword length @D0, uword initvalue @D1, uword xorout @D2) -> uword @D0 {
        ; Calculates the CRC16 checksum of the buffer using a byte lookup table.
        ; For XMODEM type checksum, use initvalue=0 and xorout=0.
        ; For IBM-3740 type checksum, use initvalue=$ffff and xorout=0.
        %asm {{
            move.w  d3,-(sp)
            move.w  d4,-(sp)
            lea     math.crc16_table,a1
            move.w  d1,d3           ; running crc = initvalue
.loop:
            subq.w  #1,d0
            bcs     .done
            move.w  d3,d1           ; copy crc
            lsr.w   #8,d1           ; index high byte
            move.b  (a0)+,d4        ; next data byte
            eor.b   d4,d1           ; table index
            lsl.w   #8,d3           ; crc <<= 8
            and.w   #$ff,d1
            add.w   d1,d1           ; word index
            move.w  (a1,d1.w),d1
            eor.w   d1,d3           ; update crc
            bra     .loop
.done:
            move.w  d3,d0
            eor.w   d2,d0           ; apply xorout
            move.w  (sp)+,d4
            move.w  (sp)+,d3
            rts
        }}
    }

    sub crc16_start(uword initvalue) {
        ; start the "streaming" crc16
        ; note: tracks the crc16 checksum in the module-internal crc16_state variable
        crc16_state = initvalue
    }

    asmsub crc16_update(ubyte value @D0) {
        ; update the "streaming" crc16 with next byte value
        ; note: tracks the crc16 checksum in the module-internal crc16_state variable
        %asm {{
            lea     math.crc16_table,a0
            move.w  math.crc16_state,d1
            lsr.w   #8,d1           ; high byte of crc
            eor.b   d0,d1           ; table index
            move.w  math.crc16_state,d0
            lsl.w   #8,d0           ; crc <<= 8
            and.w   #$ff,d1
            add.w   d1,d1           ; word index
            move.w  (a0,d1.w),d1
            eor.w   d1,d0           ; new crc
            move.w  d0,math.crc16_state
            rts
        }}
    }

    sub crc16_end(uword xorout) -> uword {
        ; finalize the "streaming" crc16, returns resulting crc16 value
        return crc16_state ^ xorout
    }

    asmsub crc32(^^ubyte data @A0, uword length @D0) -> long @D0 {
        ; Calculates the CRC-32 (ISO-HDLC/PKZIP) checksum of the buffer using a byte lookup table.
        %asm {{
            move.l  d2,-(sp)
            lea     math.crc32_table,a1
            move.l  #$ffffffff,d1   ; running crc
.loop:
            subq.w  #1,d0
            bcs     .done
            move.b  (a0)+,d2        ; next data byte
            eor.b   d1,d2           ; table index = (crc ^ byte) & $ff
            lsr.l   #8,d1           ; crc >>= 8
            and.l   #$ff,d2
            lsl.l   #2,d2           ; long index
            move.l  (a1,d2.l),d2
            eor.l   d2,d1           ; update crc
            bra     .loop
.done:
            move.l  d1,d0
            eor.l   #$ffffffff,d0   ; final xor
            move.l  (sp)+,d2
            rts
        }}
    }

    sub crc32_start() {
        ; start the "streaming" crc32
        ; note: tracks the crc32 checksum in the module-internal crc32_state variable
        crc32_state = $ffffffff
    }

    asmsub crc32_update(ubyte value @D0) {
        ; update the "streaming" crc32 with next byte value
        ; note: tracks the crc32 checksum in the module-internal crc32_state variable
        %asm {{
            lea     math.crc32_table,a0
            move.l  math.crc32_state,d1
            eor.b   d1,d0           ; table index = (crc ^ byte) & $ff
            lsr.l   #8,d1           ; crc >>= 8
            and.l   #$ff,d0
            lsl.l   #2,d0           ; long index
            move.l  (a0,d0.l),d0
            eor.l   d0,d1           ; new crc
            move.l  d1,math.crc32_state
            rts
        }}
    }

    sub crc32_end() -> long {
        ; finalize the "streaming" crc32 and return the result
        return crc32_state ^ $ffffffff
    }
}
