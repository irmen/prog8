; Internal Math library routines - always included by the compiler
; note: some functions you might expect here are builtin functions,
;       such as abs, sqrt, clamp, min, max for example.

math {
    %option ignore_unused

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
        ubyte[256] sintab = [
            $00, $03, $06, $09,
            $0c, $0f, $12, $15, $18, $1b, $1e, $21, $24, $27, $2a, $2d, $30, $33, $36, $39,
            $3b, $3e, $41, $43, $46, $49, $4b, $4e, $50, $52, $55, $57, $59, $5b, $5e, $60,
            $62, $64, $66, $67, $69, $6b, $6c, $6e, $70, $71, $72, $74, $75, $76, $77, $78,
            $79, $7a, $7b, $7b, $7c, $7d, $7d, $7e, $7e, $7e, $7e, $7e, $7f, $7e, $7e, $7e,
            $7e, $7e, $7d, $7d, $7c, $7b, $7b, $7a, $79, $78, $77, $76, $75, $74, $72, $71,
            $70, $6e, $6c, $6b, $69, $67, $66, $64, $62, $60, $5e, $5b, $59, $57, $55, $52,
            $50, $4e, $4b, $49, $46, $43, $41, $3e, $3b, $39, $36, $33, $30, $2d, $2a, $27,
            $24, $21, $1e, $1b, $18, $15, $12, $0f, $0c, $09, $06, $03, $00, $fd, $fa, $f7,
            $f4, $f1, $ee, $eb, $e8, $e5, $e2, $df, $dc, $d9, $d6, $d3, $d0, $cd, $ca, $c7,
            $c5, $c2, $bf, $bd, $ba, $b7, $b5, $b2, $b0, $ae, $ab, $a9, $a7, $a5, $a2, $a0,
            $9e, $9c, $9a, $99, $97, $95, $94, $92, $90, $8f, $8e, $8c, $8b, $8a, $89, $88,
            $87, $86, $85, $85, $84, $83, $83, $82, $82, $82, $82, $82, $81, $82, $82, $82,
            $82, $82, $83, $83, $84, $85, $85, $86, $87, $88, $89, $8a, $8b, $8c, $8e, $8f,
            $90, $92, $94, $95, $97, $99, $9a, $9c, $9e, $a0, $a2, $a5, $a7, $a9, $ab, $ae,
            $b0, $b2, $b5, $b7, $ba, $bd, $bf, $c2, $c5, $c7, $ca, $cd, $d0, $d3, $d6, $d9,
            $dc, $df, $e2, $e5, $e8, $eb, $ee, $f1, $f4, $f7, $fa, $fd
        ]
        return sintab[angle] as byte
    }

    sub cos8(ubyte angle) -> byte {
        ubyte[256] costab = [
            $7f, $7e, $7e, $7e,
            $7e, $7e, $7d, $7d, $7c, $7b, $7b, $7a, $79, $78, $77, $76, $75, $74, $72, $71,
            $70, $6e, $6c, $6b, $69, $67, $66, $64, $62, $60, $5e, $5b, $59, $57, $55, $52,
            $50, $4e, $4b, $49, $46, $43, $41, $3e, $3b, $39, $36, $33, $30, $2d, $2a, $27,
            $24, $21, $1e, $1b, $18, $15, $12, $0f, $0c, $09, $06, $03, $00, $fd, $fa, $f7,
            $f4, $f1, $ee, $eb, $e8, $e5, $e2, $df, $dc, $d9, $d6, $d3, $d0, $cd, $ca, $c7,
            $c5, $c2, $bf, $bd, $ba, $b7, $b5, $b2, $b0, $ae, $ab, $a9, $a7, $a5, $a2, $a0,
            $9e, $9c, $9a, $99, $97, $95, $94, $92, $90, $8f, $8e, $8c, $8b, $8a, $89, $88,
            $87, $86, $85, $85, $84, $83, $83, $82, $82, $82, $82, $82, $81, $82, $82, $82,
            $82, $82, $83, $83, $84, $85, $85, $86, $87, $88, $89, $8a, $8b, $8c, $8e, $8f,
            $90, $92, $94, $95, $97, $99, $9a, $9c, $9e, $a0, $a2, $a5, $a7, $a9, $ab, $ae,
            $b0, $b2, $b5, $b7, $ba, $bd, $bf, $c2, $c5, $c7, $ca, $cd, $d0, $d3, $d6, $d9,
            $dc, $df, $e2, $e5, $e8, $eb, $ee, $f1, $f4, $f7, $fa, $fd, $00, $03, $06, $09,
            $0c, $0f, $12, $15, $18, $1b, $1e, $21, $24, $27, $2a, $2d, $30, $33, $36, $39,
            $3b, $3e, $41, $43, $46, $49, $4b, $4e, $50, $52, $55, $57, $59, $5b, $5e, $60,
            $62, $64, $66, $67, $69, $6b, $6c, $6e, $70, $71, $72, $74, $75, $76, $77, $78,
            $79, $7a, $7b, $7b, $7c, $7d, $7d, $7e, $7e, $7e, $7e, $7e
        ]
        return costab[angle] as byte
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
        ubyte[180] sintab = [
            $00, $04, $08, $0d,
            $11, $16, $1a, $1e, $23, $27, $2b, $2f, $33, $37, $3b, $3f, $43, $47, $4a, $4e,
            $51, $54, $58, $5b, $5e, $61, $64, $66, $69, $6b, $6d, $70, $72, $74, $75, $77,
            $78, $7a, $7b, $7c, $7d, $7d, $7e, $7e, $7e, $7f, $7e, $7e, $7e, $7d, $7d, $7c,
            $7b, $7a, $78, $77, $75, $74, $72, $70, $6d, $6b, $69, $66, $64, $61, $5e, $5b,
            $58, $54, $51, $4e, $4a, $47, $43, $3f, $3b, $37, $33, $2f, $2b, $27, $23, $1e,
            $1a, $16, $11, $0d, $08, $04, $00, $fc, $f8, $f3, $ef, $ea, $e6, $e2, $dd, $d9,
            $d5, $d1, $cd, $c9, $c5, $c1, $bd, $b9, $b6, $b2, $af, $ac, $a8, $a5, $a2, $9f,
            $9c, $9a, $97, $95, $93, $90, $8e, $8c, $8b, $89, $88, $86, $85, $84, $83, $83,
            $82, $82, $82, $81, $82, $82, $82, $83, $83, $84, $85, $86, $88, $89, $8b, $8c,
            $8e, $90, $93, $95, $97, $9a, $9c, $9f, $a2, $a5, $a8, $ac, $af, $b2, $b6, $b9,
            $bd, $c1, $c5, $c9, $cd, $d1, $d5, $d9, $dd, $e2, $e6, $ea, $ef, $f3, $f8, $fc
        ]
        return sintab[radians] as byte
    }

    sub cosr8(ubyte radians) -> byte {
        ubyte[180] costab = [
            $7f, $7e, $7e, $7e, $7d, $7d, $7c,
            $7b, $7a, $78, $77, $75, $74, $72, $70, $6d, $6b, $69, $66, $64, $61, $5e, $5b,
            $58, $54, $51, $4e, $4a, $47, $43, $3f, $3b, $37, $33, $2f, $2b, $27, $23, $1e,
            $1a, $16, $11, $0d, $08, $04, $00, $fc, $f8, $f3, $ef, $ea, $e6, $e2, $dd, $d9,
            $d5, $d1, $cd, $c9, $c5, $c1, $bd, $b9, $b6, $b2, $af, $ac, $a8, $a5, $a2, $9f,
            $9c, $9a, $97, $95, $93, $90, $8e, $8c, $8b, $89, $88, $86, $85, $84, $83, $83,
            $82, $82, $82, $81, $82, $82, $82, $83, $83, $84, $85, $86, $88, $89, $8b, $8c,
            $8e, $90, $93, $95, $97, $9a, $9c, $9f, $a2, $a5, $a8, $ac, $af, $b2, $b6, $b9,
            $bd, $c1, $c5, $c9, $cd, $d1, $d5, $d9, $dd, $e2, $e6, $ea, $ef, $f3, $f8, $fc,
            $00, $04, $08, $0d, $11, $16, $1a, $1e, $23, $27, $2b, $2f, $33, $37, $3b, $3f,
            $43, $47, $4a, $4e, $51, $54, $58, $5b, $5e, $61, $64, $66, $69, $6b, $6d, $70,
            $72, $74, $75, $77, $78, $7a, $7b, $7c, $7d, $7d, $7e, $7e, $7e
        ]
        return costab[radians] as byte
    }

    sub rnd() -> ubyte {
        %ir {{
            syscall 33 (): r0.b
            returnr.b r0
        }}
    }

    sub rndw() -> uword {
        %ir {{
            syscall 34 (): r0.w
            returnr.w r0
        }}
    }

    sub rndseed(uword seed1, uword seed2) {
        ; -- reset the pseudo RNG's seed values. Defaults are: $a55a, $7653.
        %ir {{
            loadm.w r65534,math.rndseed.seed1
            loadm.w r65535,math.rndseed.seed2
            syscall 31 (r65534.w, r65535.w)
            return
        }}
    }

    sub log2(ubyte value) -> ubyte {
        ubyte result = 7
        ubyte compare = $80
        repeat {
            if value&compare!=0
                return result
            result--
            if_z
                return 0
            compare >>= 1
        }
    }

    sub log2w(uword value) -> ubyte {
        ubyte result = 15
        uword compare = $8000
        repeat {
            if value&compare!=0
                return result
            result--
            if_z
                return 0
            compare >>= 1
        }
    }

    sub direction(ubyte x1, ubyte y1, ubyte x2, ubyte y2) -> ubyte {
        ; From a pair of positive coordinates, calculate discrete direction between 0 and 23 into A.
        ; This adjusts the atan() result  so that the direction N is centered on the angle=N instead of having it as a boundary
        ubyte angle = atan2(x1, y1, x2, y2) - 256/48
        return 23-lsb(mkword(angle,0) / 2730)
    }

    sub direction_sc(byte x1, byte y1, byte x2, byte y2) -> ubyte {
        ; From a pair of signed coordinates around the origin, calculate discrete direction between 0 and 23 into A.
        ; shift the points into the positive quadrant
        ubyte px1
        ubyte py1
        ubyte px2
        ubyte py2
        if x1<0 or x2<0 {
            px1 = x1 as ubyte + 128
            px2 = x2 as ubyte + 128
        } else {
            px1 = x1 as ubyte
            px2 = x2 as ubyte
        }
        if y1<0 or y2<0 {
            py1 = y1 as ubyte + 128
            py2 = y2 as ubyte + 128
        } else {
            py1 = y1 as ubyte
            py2 = y2 as ubyte
        }

        return direction(px1, py1, px2, py2)
    }

    sub direction_qd(ubyte quadrant, ubyte xdelta, ubyte ydelta) -> ubyte {
        ; From a pair of X/Y deltas (both >=0), and quadrant 0-3, calculate discrete direction between 0 and 23.
        when quadrant {
            3 -> return direction(0, 0, xdelta, ydelta)
            2 -> return direction(xdelta, 0, 0, ydelta)
            1 -> return direction(0, ydelta, xdelta, 0)
            else -> return direction(xdelta, ydelta, 0, 0)
        }
    }

    sub atan2(ubyte x1, ubyte y1, ubyte x2, ubyte y2) -> ubyte {
        ;; Calculate the angle, in a 256-degree circle, between two points into A.
        ;; The points (x1, y1) and (x2, y2) have to use *unsigned coordinates only* from the positive quadrant in the carthesian plane!
        %ir {{
            loadm.b r65532,math.atan2.x1
            loadm.b r65533,math.atan2.y1
            loadm.b r65534,math.atan2.x2
            loadm.b r65535,math.atan2.y2
            syscall 44 (r65532.b, r65533.b, r65534.b, r65535.b): r0.b
            returnr.b r0
        }}
    }

    sub mul16_last_upper() -> uword {
        ; This routine peeks into the internal 32 bits multiplication result buffer of the
        ; 16*16 bits multiplication routine, to fetch the upper 16 bits of the last calculation.
        ; Notes:
        ;   - to avoid interference it's best to fetch and store this value immediately after the multiplication expression.
        ;     for instance, simply printing a number may already result in new multiplication calls being performed
        ;   - not all multiplications in the source code result in an actual multiplication call:
        ;     some simpler multiplications will be optimized away into faster routines. These will not set the upper 16 bits at all!
        %ir {{
            syscall 46 (): r0.w
            returnr.w r0
        }}
    }

    sub diff(ubyte b1, ubyte b2) -> ubyte {
        if b1>b2
            return b1-b2
        return b2-b1
    }

    sub diffw(uword w1, uword w2) -> uword {
        if w1>w2
            return w1-w2
        return w2-w1
    }

    sub crc16(uword data, uword length) -> uword {
        ; calculates the CRC16 (XMODEM) checksum of the buffer.
        cx16.r0 = 0  ; the crc value
        repeat length {
            cx16.r0H ^= @(data)
            repeat 8 {
                if cx16.r0H & $80 !=0
                    cx16.r0 = (cx16.r0<<1)^$1021
                else
                    cx16.r0<<=1
            }
            data++
        }
        return cx16.r0
    }

    sub crc32(uword data, uword length) {
        ; Calculates the CRC-32 (POSIX) checksum of the buffer.
        ; because prog8 doesn't have 32 bits integers, we have to split up the calculation over 2 words.
        ; result stored in cx16.r0 (low word) and cx16.r1 (high word)
        cx16.r1 = 0
        cx16.r0 = 0
        repeat length {
            cx16.r1H ^= @(data)
            repeat 8 {
                if cx16.r1H & $80 !=0 {
                    cx16.r0 <<= 1
                    rol(cx16.r1)
                    cx16.r1 ^= $04c1
                    cx16.r0 ^= $1db7
                }
                else {
                    cx16.r0 <<= 1
                    rol(cx16.r1)
                }
            }
            data++
        }
        cx16.r1 ^= $ffff
        cx16.r0 ^= $ffff
    }
}
