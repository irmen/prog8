%import textio
%import emudbg
%zeropage basicsafe

main {
    sub start() {
        ubyte ub1, ub2

        for ub1 in 0 to 255 {
            for ub2 in 0 to 255 {
                cx16.r0L = ub1 % ub2
                cx16.r1L = mymod(ub1, ub2)
                if cx16.r0L != cx16.r1L {
                    txt.print("different ")
                    txt.print_ub(ub1)
                    txt.spc()
                    txt.print_ub(ub2)
                    txt.nl()
                }
            }
        }
        txt.print("all good\n")

        ub1 = 65
        ub2 = 2

        txt.print_ub(ub1 % ub2)
        txt.spc()
        txt.print_ub(mymod(ub1, ub2))
        txt.nl()

        repeat 3 {
            sys.set_irqd()
            emudbg.reset_cpu_cycles()
            repeat 1000 {
                ub1 %= ub2
            }
            cx16.r4, cx16.r5 = emudbg.cpu_cycles()
            sys.clear_irqd()
            txt.print_uwhex(cx16.r5, true)
            txt.print_uwhex(cx16.r4, false)
            txt.nl()
        }

        repeat 3 {
            sys.set_irqd()
            emudbg.reset_cpu_cycles()
            repeat 1000 {
                ub1 = mymod(ub1, ub2)
            }
            cx16.r4, cx16.r5 = emudbg.cpu_cycles()
            sys.clear_irqd()
            txt.print_uwhex(cx16.r5, true)
            txt.print_uwhex(cx16.r4, false)
            txt.nl()
        }
    }

    asmsub  mymod(ubyte u1 @A, ubyte u2 @Y) -> ubyte @A {
        %asm {{
	cpy  #0
	beq  _zero
	cpy  #1
	bne  +
	lda  #0
	rts
+       cpy  #2
	bne  +
	and  #1
	rts
+       sty  P8ZP_SCRATCH_REG
	sec
-       sbc  P8ZP_SCRATCH_REG
	bcs  -
	adc  P8ZP_SCRATCH_REG
_zero   rts
        }}
    }
}
