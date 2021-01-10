%import test_stack
%import textio
%import diskio
%import string
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        str s1 = "12345 abcdef..uvwxyz ()!@#$%;:&*()-=[]<>\xff\xfa\xeb\xc0\n"
        str s2 = "12345 ABCDEF..UVWXYZ ()!@#$%;:&*()-=[]<>\xff\xfa\xeb\xc0\n"
        str s3 = "12345 \x61\x62\x63\x64\x65\x66..\x75\x76\x77\x78\x79\x7a ()!@#$%;:&*()-=[]<>\xff\xfa\xeb\xc0\n"

        lower(s1)
        lower(s2)
        lower(s3)

        txt.lowercase()
        txt.print(s1)
        txt.print(s2)
        txt.print(s3)

        txt.nl()
    }

    asmsub lower(uword st @AY) clobbers(X) {
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            beq  _done
            and  #$7f
            tax
            and  #%11100000
            cmp  #%01100000
            bne  +
            txa
            and  #%11011111
            tax
+           txa
            sta  (P8ZP_SCRATCH_W1),y
            iny
            bne  -
_done       rts
        }}
    }

    asmsub upper(uword st @AY) clobbers(X) {
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            beq  _done
            and  #$7f
            tax
            and  #%11100000
            cmp  #%01100000
            bne  +
            txa
            and  #%11011111
            tax
+           txa
            sta  (P8ZP_SCRATCH_W1),y
            iny
            bne  -
_done       rts
        }}
    }
}
