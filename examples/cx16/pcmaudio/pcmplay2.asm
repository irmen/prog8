;
; This program plays a short mono 16 bit PCM sample on repeat
; It uses the AFLOW IRQ that signals when the Fifo is about to drain empty.
; The flashing green bar is when the routine is busy copying sample data into the fifo.
;
; source code is for 64TASS, assemble with: 64tass pcmplay2.asm -o pcmplay2.prg
;

.cpu  'w65c02'
.enc  'none'

* = $0801
	; basic launcher
	.word  $080b, 2022
	.text  $9e, "2061", $00
	.word  0
entrypoint:


CHROUT = $ffd2
VERA_ADDR_L = $9f20
VERA_ADDR_M = $9f21
VERA_ADDR_H = $9f22
VERA_DATA0 = $9f23
VERA_CTRL = $9f25
VERA_IEN = $9f26
VERA_ISR = $9f27
VERA_AUDIO_CTRL = $9f3b
VERA_AUDIO_RATE = $9f3c
VERA_AUDIO_DATA = $9f3d
IRQ_VECTOR = $0314
r0  = $02
r0L = $02
r0H = $03
r1  = $04
r1L = $04
r1H = $05
pcm_ptr = $06


	; stop playback and select mono 16 bit, max volume
	stz  VERA_AUDIO_RATE
	lda  #%10101111
	sta  VERA_AUDIO_CTRL

	; fill the fifo with some silence
	ldy  #1024/256
_z1:	ldx  #0
_z2:	stz  VERA_AUDIO_DATA
	dex
	bne  _z2
	dey
	bne  _z1

	ldx  #<audio_on_txt
	ldy  #>audio_on_txt
	jsr  print

	ldx  #<pcm_data
	ldy  #>pcm_data
	stx  pcm_ptr
	sty  pcm_ptr+1

	; set interrupt handler
	sei
 	ldx  #<irq_handler
 	ldy  #>irq_handler
 	stx  IRQ_VECTOR
 	sty  IRQ_VECTOR+1
	lda  #%00001000     ; enable the AFLOW irq
	sta  VERA_IEN
 	cli

	lda  #21
	sta  VERA_AUDIO_RATE    ; start playback

_wait:
	wai
	bra _wait


irq_handler:
	lda  VERA_ISR
	and  #%00001000		; is aflow?
	bne  _aflow_irq
	; handle other irq here
	bra  _exit_irq

_aflow_irq:
	; paint a screen color
	stz  VERA_CTRL
	lda  #$0c
	sta  VERA_ADDR_L
	lda  #$fa
	sta  VERA_ADDR_M
	lda  #$01
	sta  VERA_ADDR_H
	lda  #$a0
	sta  VERA_DATA0

	; refill fifo buffer (minimum 1Kb = 1/4)
	; this assumes all samples are aligned to page size!
        ; so we can at least copy a full page at once here.
        ldx  #8                 ; number of pages to copy
_copy_more_pages:
	ldy  #0
_c1:	lda  (pcm_ptr),y
	sta  VERA_AUDIO_DATA    ; lsb
	iny
	lda  (pcm_ptr),y
	sta  VERA_AUDIO_DATA    ; msb
	iny
	; (in case of 16 bit stereo output, this can be unrolled by 2 bytes again).
	bne  _c1
	inc  pcm_ptr+1

	; have we reached the end of the sample data?  (pcm_ptr >= pcm_data_end)
	; due to page size alignment only the MSB has to be checked
	lda  pcm_ptr+1
	cmp  #>pcm_data_end
	bcc  _not_at_end
	bne  _end_reached
;	lda  pcm_ptr            ; uncomment if lsb also needs to be checked
;	cmp  #<pcm_data_end
;	bcs  _end_reached

_not_at_end:
	; note: filling the fifo until the Fifo Full bit is set doesn't seem to work reliably
	; so we just fill it with a fixed number of samples.
	; in this case 8 pages = 2Kb which fills the fifo back up to 50%-75%.
	dex
	bne  _copy_more_pages

	lda  #$00
	sta  VERA_DATA0         ; back to other screen color

_exit_irq:
	ply
	plx
	pla
	rti

_end_reached:
	lda  #<pcm_data
	sta  pcm_ptr
	lda  #>pcm_data
	sta  pcm_ptr+1
	bra  _not_at_end


audio_on_txt:	.text "AUDIO ON (IRQ)", $0d, $00

print:	; -- print string pointed to by X/Y
	stx  r0L
	sty  r0H
	ldy  #0
_chr:	lda  (r0),y
	beq  _done
	jsr  $ffd2
	iny
	bne  _chr
_done:	rts

	.align $0100
pcm_data:
	.binary "small-pcm-mono.bin"
pcm_data_end:
