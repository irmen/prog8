%import textio
%import compression
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str data = "a..........irmen????????zzzzz!"
        str output = "?" * 100
        str decompressed = "\x00" * 100

        uword ptr

        uword csize = compression.encode_rle(data, len(data), output, true)
        txt.print_ub(len(data))
        txt.spc()
        txt.print_uw(csize)
        txt.nl()
        txt.nl()
        ptr = &output - 1
        csize = compression.decode_rle_srcfunc(&srcfunc, decompressed, len(decompressed))
        txt.print_uw(csize)
        txt.nl()
        txt.print(data)
        txt.nl()
        txt.print(decompressed)
        txt.nl()

        asmsub srcfunc() -> ubyte @A {
            %asm {{
                inc  p8v_ptr
                bne  +
                inc  p8v_ptr+1
+               ldy  #0
                lda  (p8v_ptr),y
                rts
            }}
        }
    }
}
