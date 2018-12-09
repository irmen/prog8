; Prog8 internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


~ prog8_lib {
		; @TODO move all this assembly to a real .asm file instead and include that...
	

		; note: the following ZP scratch registers must be the same as in c64lib
		memory  ubyte  SCRATCH_ZPB1	= $02		; scratch byte 1 in ZP
		memory  ubyte  SCRATCH_ZPREG	= $03		; scratch register in ZP
		memory  uword  SCRATCH_ZPWORD1	= $fb		; scratch word in ZP ($fb/$fc)
		memory  uword  SCRATCH_ZPWORD2	= $fd		; scratch word in ZP ($fd/$fe)
		const   uword  ESTACK_LO	= $ce00
		const   uword  ESTACK_HI	= $cf00


	%asm {{

; 16-bit rotate right (as opposed to the 6502's usual 17-bit rotate with carry)
; the word is placed in SCRATCH_ZPWORD1
ror2_word	.proc
		lsr  SCRATCH_ZPWORD1+1
		ror  SCRATCH_ZPWORD1
		bcc  +
		lda  SCRATCH_ZPWORD1+1
		ora  #$80
		sta  SCRATCH_ZPWORD1+1
+		rts
		.pend



; @todo:  implement stubs!
; @todo:  move float operations to their own library (only included when floats are enabled)

ub2float	.proc
		rts
		.warn "not implemented"
		.pend

b2float		.proc
		rts
		.warn "not implemented"
		.pend

uw2float	.proc
		rts
		.warn "not implemented"
		.pend

w2float		.proc
		rts
		.warn "not implemented"
		.pend

push_float	.proc
		; ---- push mflpt5 in A/Y onto stack 
		; (taking 3 stack positions = 6 bytes of which 1 is padding)
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		ldy  #0
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_LO,x
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_HI,x
		dex
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_LO,x
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_HI,x
		dex
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_LO,x
		dex
		rts
		.pend
		
		
push_float_from_indexed_var	.proc
		rts
		.warn "not implemented"
		.pend

pop_float	.proc
		; ---- pops mflpt5 from stack to memory A/Y
		; (frees 3 stack positions = 6 bytes of which 1 is padding)
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		ldy  #4
		inx
		lda  ESTACK_LO,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		inx
		lda  ESTACK_HI,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		lda  ESTACK_LO,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		inx
		lda  ESTACK_HI,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		lda  ESTACK_LO,x
		sta  (SCRATCH_ZPWORD1),y
		rts
		.pend
		
pop_float_to_indexed_var	.proc
		rts
		.warn "not implemented"
		.pend

pop_mem_float	.proc
		rts
		.warn "not implemented"
		.pend

copy_float	.proc
		; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1, 
		;    into the 5 bytes pointed to by A/Y.  Clobbers Y.
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		ldy  #0
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		rts
		.pend

inc_var_f	.proc
		rts
		.warn "not implemented"
		.pend
                
dec_var_f	.proc
		rts
		.warn "not implemented"
		.pend

div_f		.proc
		rts
		.warn "not implemented"
		.pend

add_f		.proc
		rts
		.warn "not implemented"
		.pend

sub_f		.proc
		rts
		.warn "not implemented"
		.pend

mul_f		.proc
		rts
		.warn "not implemented"
		.pend

neg_f		.proc
		rts
		.warn "not implemented"
		.pend

		
add_w		.proc
		rts	; @todo inline?
		.warn "not implemented"
		.pend
		
add_uw		.proc
		rts	; @todo inline?
		.warn "not implemented"
		.pend
		
sub_w		.proc
		rts	; @todo inline?
		.warn "not implemented"
		.pend

sub_uw		.proc
		rts	; @todo inline?
		.warn "not implemented"
		.pend

mul_b		.proc
		rts
		.warn "not implemented"
		.pend
		
mul_ub		.proc
		rts
		.warn "not implemented"
		.pend
		
mul_w		.proc
		rts
		.warn "not implemented"
		.pend
		
mul_uw		.proc
		rts
		.warn "not implemented"
		.pend

div_b		.proc
		rts
		.warn "not implemented"
		.pend
		
div_ub		.proc
		rts
		.warn "not implemented"
		.pend
		
div_w		.proc
		rts
		.warn "not implemented"
		.pend
		
div_uw		.proc
		rts
		.warn "not implemented"
		.pend

remainder_b	.proc
		rts
		.warn "not implemented"
		.pend
		
remainder_ub	.proc
		rts
		.warn "not implemented"
		.pend
		
remainder_w	.proc
		rts
		.warn "not implemented"
		.pend
		
remainder_uw	.proc
		rts
		.warn "not implemented"
		.pend
		
remainder_f	.proc
		rts
		.warn "not implemented"
		.pend
		

equal_w		.proc
		; -- are the two words on the stack identical?
		; @todo optimize according to http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		bne  equal_b._equal_b_false
		lda  ESTACK_HI+1,x
		cmp  ESTACK_HI+2,x
		bne  equal_b._equal_b_false
		beq  equal_b._equal_b_true
		.pend

notequal_b	.proc
		; -- are the two bytes on the stack different?
		inx
		lda  ESTACK_LO,x
		eor  ESTACK_LO+1,x
		sta  ESTACK_LO+1,x
		rts
		.pend
		
notequal_w	.proc
		; -- are the two words on the stack different?
		inx
		lda  ESTACK_LO,x
		eor  ESTACK_LO+1,x
		beq  +
		sta  ESTACK_LO+1,x
		rts
+		lda  ESTACK_HI,x
		eor  ESTACK_HI+1,x
		sta  ESTACK_LO+1,x
		rts
		.pend
		
less_ub		.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend
	
less_b		.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		sec
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

less_uw		.proc
		lda  ESTACK_HI+2,x
		cmp  ESTACK_HI+1,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

less_w		.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		lda  ESTACK_HI+2,x
		sbc  ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

equal_b		.proc
		; -- are the two bytes on the stack identical?
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bne  _equal_b_false
_equal_b_true	lda  #1
_equal_b_store	inx
		sta  ESTACK_LO+1,x
		rts
_equal_b_false	lda  #0
		beq  _equal_b_store
		.pend

lesseq_ub	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		beq  equal_b._equal_b_true		; @todo optimize by flipping comparison
		bcs  equal_b._equal_b_false
		.pend
	
lesseq_b	.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		clc
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

lesseq_uw	.proc
		lda  ESTACK_HI+1,x
		cmp  ESTACK_HI+2,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend
		
lesseq_w	.proc
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		lda  ESTACK_HI+1,x
		sbc  ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_ub	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		beq  equal_b._equal_b_false
		bcs  equal_b._equal_b_true		; @todo optimize by flipping comparison?
		bcc  equal_b._equal_b_false
		.pend
	
greater_b	.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		clc
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_uw	.proc
		lda  ESTACK_HI+1,x
		cmp  ESTACK_HI+2,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

greater_w	.proc
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		lda  ESTACK_HI+1,x
		sbc  ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend
	
greatereq_ub	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend
	
greatereq_b	.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		sec
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greatereq_uw	.proc
		lda  ESTACK_HI+2,x
		cmp  ESTACK_HI+1,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greatereq_w	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		lda  ESTACK_HI+2,x
		sbc  ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

equal_f		.proc
		; -- are the two mflpt5 numbers on the stack identical?
		inx
		inx
		inx
		inx
		lda  ESTACK_LO-3,x
		cmp  ESTACK_LO,x
		bne  equal_b._equal_b_false
		lda  ESTACK_LO-2,x
		cmp  ESTACK_LO+1,x
		bne  equal_b._equal_b_false
		lda  ESTACK_LO-1,x
		cmp  ESTACK_LO+2,x
		bne  equal_b._equal_b_false
		lda  ESTACK_HI-2,x
		cmp  ESTACK_HI+1,x
		bne  equal_b._equal_b_false
		lda  ESTACK_HI-1,x
		cmp  ESTACK_HI+2,x
		bne  equal_b._equal_b_false
		beq  equal_b._equal_b_true
		.pend

notequal_f	.proc
		; -- are the two mflpt5 numbers on the stack different?
		jsr  equal_f
		eor  #1		; invert the result
		sta  ESTACK_LO+1,x
		rts
		.pend

less_f		.proc
		; -- is f1 < f2?
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend
		

lesseq_f	.proc
		; -- is f1 <= f2?
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greater_f	.proc
		; -- is f1 > f2?
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greatereq_f	.proc
		; -- is f1 >= f2?
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

compare_floats	.proc
		lda  #<_flt2
		ldy  #>_flt2
		jsr  pop_float
		lda  #<_flt1
		ldy  #>_flt1
		jsr  pop_float
		lda  #<_flt1
		ldy  #>_flt1
		jsr  c64.MOVFM		; fac1 = flt1
		lda  #<_flt2
		ldy  #>_flt2
		stx  SCRATCH_ZPREG
		jsr  c64.FCOMP		; A = flt1 compared with flt2 (0=equal, 1=flt1>flt2, 255=flt1<flt2)
		ldx  SCRATCH_ZPREG
		rts
_flt1		.fill 5
_flt2		.fill 5
_return_false	lda  #0
_return_result  sta  ESTACK_LO,x
		dex
		rts
_return_true	lda  #1
		bne  _return_result
		.pend		
		

func_sin	.proc
		rts
		.warn "not implemented"
		.pend

func_cos	.proc
		rts
		.warn "not implemented"
		.pend
		
func_abs	.proc
		rts
		.warn "not implemented"
		.pend
		
func_acos	.proc
		rts
		.warn "not implemented"
		.pend
		
func_asin	.proc
		rts
		.warn "not implemented"
		.pend
		
func_tan	.proc
		rts
		.warn "not implemented"
		.pend
		
func_atan	.proc
		rts
		.warn "not implemented"
		.pend
		
func_ln		.proc
		rts
		.warn "not implemented"
		.pend
		
func_log2	.proc
		rts
		.warn "not implemented"
		.pend
		
func_log10	.proc
		rts
		.warn "not implemented"
		.pend
		
func_sqrt	.proc
		rts
		.warn "not implemented"
		.pend
		
func_rad	.proc
		rts
		.warn "not implemented"
		.pend
		
func_deg	.proc
		rts
		.warn "not implemented"
		.pend
		
func_round	.proc
		rts
		.warn "not implemented"
		.pend
		
func_floor	.proc
		rts
		.warn "not implemented"
		.pend
		
func_ceil	.proc
		rts
		.warn "not implemented"
		.pend
		
func_max	.proc
		rts
		.warn "not implemented--what does it max over???"
		.pend
		
func_min	.proc
		rts
		.warn "not implemented--what does it min over???"
		.pend
		
func_avg	.proc
		rts
		.warn "not implemented--what does it avg over???"
		.pend
		
func_sum	.proc
		rts
		.warn "not implemented--what does it sum over???"
		.pend
		
func_len	.proc
		rts
		.warn "not implemented--of what does it take len?"
		.pend
		
func_any	.proc
		rts
		.warn "not implemented--of what does it do any?"
		.pend
		
func_all	.proc
		rts
		.warn "not implemented--of what does it do all?"
		.pend
		

func_rnd	.proc
		; -- put a random ubyte on the estack
		jsr  math.randbyte
		sta  ESTACK_LO,x
		dex
		rts
		.pend
		
func_rndw	.proc
		; -- put a random uword on the estack
		jsr  math.randword
		sta  ESTACK_LO,x
		tya
		sta  ESTACK_HI,x
		dex
		rts
		.pend

func_rndf	.proc
		; -- put a random floating point value on the stack
		stx  SCRATCH_ZPREG
		lda  #1
		jsr  c64.FREADSA
		jsr  c64.RND		; rng into fac1
		ldx  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jsr  c64.FTOMEMXY	; fac1 to mem X/Y
		ldx  SCRATCH_ZPREG
		lda  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jmp  push_float
_rndf_rnum5	.fill 5
		.pend


func_wrd	.proc
		rts
		.warn "not implemented"
		.pend

func_uwrd	.proc
		rts
		.warn "not implemented"
		.pend

func_str2byte	.proc
		rts
		.warn "not implemented"
		.pend

func_str2ubyte	.proc
		rts
		.warn "not implemented"
		.pend

func_str2word	.proc
		rts
		.warn "not implemented"
		.pend

func_str2uword	.proc
		rts
		.warn "not implemented"
		.pend

func_str2float	.proc
		rts
		.warn "not implemented"
		.pend

    
	}}
}
