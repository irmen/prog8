
	* = $8000

	sec
	lda  #$a0
	sta  $2000
	ldx  $2000
	txa
	tay
	iny
	sty  $2001
	ldy  #0
loop	lda  text,y
	beq  end
	sta  $d000
	inc  $d001
	iny
	jmp  loop
end	nop
	bvs  loop

	.byte $02	; invalid opcode
	.byte $02	; invalid opcode
	.byte $02	; invalid opcode


text	.enc "screen"
	.text "hello!",0
	.enc "none"
