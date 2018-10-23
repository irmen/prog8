; Prog8 internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


~ prog8_lib {
		; note: the following ZP scratch registers must be the same as in c64lib
		memory  ubyte  SCRATCH_ZP1	= $02		; scratch register #1 in ZP
		memory  ubyte  SCRATCH_ZP2	= $03		; scratch register #2 in ZP
		memory  uword  SCRATCH_ZPWORD1	= $fb		; scratch word in ZP ($fb/$fc)
		memory  uword  SCRATCH_ZPWORD2	= $fd		; scratch word in ZP ($fd/$fe)


	%asm {{

; 16-bit rotate right (as opposed to the 6502's usual 17-bit rotate with carry)
; the word is placed in SCRATCH_ZPWORD1
ror2_word	
		lsr  SCRATCH_ZPWORD1+1
		ror  SCRATCH_ZPWORD1
		bcc  +
		lda  SCRATCH_ZPWORD1+1
		ora  #$80
		sta  SCRATCH_ZPWORD1+1
+		rts



; @todo:  stubs for now

ub2float
		rts

uw2float
		rts

push_float
		rts

pop_var_float
		rts

copy_float
		rts

inc_var_f
		rts
                
dec_var_f
		rts

div_f
		rts

add_f
		rts

sub_f
		rts

mul_f
		rts

sub_uw
		rts

less_ub
		rts

less_f
		rts

	}}
}
