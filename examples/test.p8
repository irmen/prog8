%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        ubyte rr = bla(1,true,2)
        txt.print_ub(rr)
        txt.nl()
        rr = bla(1,false,2)
        txt.print_ub(rr)
    }

    asmsub bla(ubyte aa @A, ubyte cc @Pc, ubyte bb @Y) -> ubyte @A{
        %asm {{
            bcc  +
            lda  #10
            bne  end
+           lda  #20
end         rts
        }}
    }
}
