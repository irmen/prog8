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
		const   uword  ESTACK_LO	= $ce00
		const   uword  ESTACK_HI	= $cf00


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



; @todo:  implement stubs!
; @todo:  move float operations to their own library (only included when floats are enabled)

ub2float
		rts

b2float
		rts

uw2float
		rts

w2float
		rts

push_float
		; ---- push mflpt5 in A/Y onto stack 
		; (taking 3 stack positions = 6 bytes of which 1 is padding)
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		ldy  #0
		lda  SCRATCH_ZPWORD1,y
		sta  ESTACK_LO,x
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  ESTACK_HI,x
		dex
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  ESTACK_LO,x
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  ESTACK_HI,x
		dex
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  ESTACK_LO,x
		dex
		rts
		
		
push_float_from_indexed_var
		rts

pop_float
		; ---- pops mflpt5 from stack to memory A/Y		@TODO CHECK ORDER OF POPS
		; (frees 3 stack positions = 6 bytes of which 1 is padding)
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		ldy  #4
		inx
		lda  ESTACK_LO,x
		sta  SCRATCH_ZPWORD1,y
		dey
		inx
		lda  ESTACK_LO,x
		sta  SCRATCH_ZPWORD1,y
		dey
		lda  ESTACK_HI,x
		sta  SCRATCH_ZPWORD1,y
		dey
		inx
		lda  ESTACK_LO,x
		sta  SCRATCH_ZPWORD1,y
		lda  ESTACK_HI,x
		dey
		sta  SCRATCH_ZPWORD1,y
		rts
		
pop_float_to_indexed_var
		rts

pop_mem_float
		rts

copy_float
		; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1, 
		;    into the 5 bytes pointed to by A/Y.  Clobbers Y.
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		ldy  #0
		lda  SCRATCH_ZPWORD1,y
		sta  SCRATCH_ZPWORD2,y
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  SCRATCH_ZPWORD2,y
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  SCRATCH_ZPWORD2,y
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  SCRATCH_ZPWORD2,y
		iny
		lda  SCRATCH_ZPWORD1,y
		sta  SCRATCH_ZPWORD2,y
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

neg_f
		rts

		
add_w
		rts	; @todo inline?
add_uw
		rts	; @todo inline?
		
sub_w
		rts	; @todo inline?
sub_uw
		rts	; @todo inline?

mul_b
		rts
mul_ub
		rts
mul_w
		rts
mul_uw
		rts

div_b
		rts
div_ub
		rts
div_w
		rts
div_uw
		rts

remainder_b
		rts
remainder_ub
		rts
remainder_w
		rts
remainder_uw
		rts
remainder_f
		rts
		
equal_ub
		rts
	
equal_b
		rts

equal_w
		rts

equal_uw
		rts

equal_f
		rts

less_ub
		rts
	
less_b
		rts

less_w
		rts

less_uw
		rts

less_f
		rts

lesseq_ub
		rts
	
lesseq_b
		rts

lesseq_w
		rts

lesseq_uw
		rts

lesseq_f
		rts
	
greater_ub
		rts
	
greater_b
		rts

greater_w
		rts

greater_uw
		rts

greater_f
		rts
	
greatereq_ub
		rts
	
greatereq_b
		rts

greatereq_w
		rts

greatereq_uw
		rts

greatereq_f
		rts

func_sin
		rts
func_cos
		rts
func_abs
		rts
func_acos
		rts
func_asin
		rts
func_tan
		rts
func_atan
		rts
func_ln
		rts
func_log2
		rts
func_log10
		rts
func_sqrt
		rts
func_rad
		rts
func_deg
		rts
func_round
		rts
func_floor
		rts
func_ceil
		rts
func_max
		rts
func_min
		rts
func_avg
		rts
func_sum
		rts
func_len
		rts
func_any
		rts
func_all
		rts

func_rnd
		; -- put a random ubyte on the estack
		jsr  math.randbyte
		sta  ESTACK_LO,x
		dex
		rts
		
func_rndw
		; -- put a random uword on the estack
		jsr  math.randword
		sta  ESTACK_LO,x
		tya
		sta  ESTACK_HI,x
		dex
		rts

func_rndf
		; -- put a random floating point value on the stack
		stx  SCRATCH_ZP1
		lda  #39
		jsr  c64.RNDA		; rng into fac1
		ldx  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jsr  c64.FTOMEMXY	; fac1 to mem X/Y
		ldx  SCRATCH_ZP1
		lda  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jmp  push_float
_rndf_rnum5	.fill 5


func_wrd
		rts
func_uwrd
		rts
func_str2byte
		rts
func_str2ubyte
		rts
func_str2word
		rts
func_str2uword
		rts
func_str2float
		rts
    
	}}
}
