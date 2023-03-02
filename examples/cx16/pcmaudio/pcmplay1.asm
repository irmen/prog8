;
; This program plays a short mono 16 bit PCM sample on repeat
; It uses no IRQs but just checks the Fifo Full/Empty bits to see when it needs to copy more data.
; The flashing green bar is when the routine is busy copying sample data into the fifo.
;
; source code is for 64TASS, assemble with: 64tass pcmplay1.asm -o pcmplay1.prg
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

	; fill the fifo with some of silence
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

	lda  #21
	sta  VERA_AUDIO_RATE            ; start playing

play_loop:
_wait_for_empty_fifo:
	bit  VERA_AUDIO_CTRL            ; fifo empty?
	bvc  _wait_for_empty_fifo

	; fifo is empty, we go ahead and fill it with more sound data.
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


_copy_samples:
	; this assumes all samples are aligned to page size!
        ; so we can at least copy a full page at once here.
	ldy  #0
_c1	lda  (pcm_ptr),y
	sta  VERA_AUDIO_DATA    ; lsb
	iny
	lda  (pcm_ptr),y
	sta  VERA_AUDIO_DATA    ; msb
	iny
	bne  _c1
	inc  pcm_ptr+1

	; have we reached the end of the sample data?  (pcm_ptr >= pcm_data_end)
	; due to page size alignment only the MSB has to be checked
	lda  pcm_ptr+1
	cmp  #>pcm_data_end
	bcc  _continue
	bne  _end_reached
;	lda  pcm_ptr            ; uncomment if lsb also needs to be checked
;	cmp  #<pcm_data_end
;	bcs  _end_reached
_continue:
	bit  VERA_AUDIO_CTRL    ; is fifo full?
	bpl  _copy_samples      ; no, continue copying

	lda  #$00
	sta  VERA_DATA0         ; back to other screen color
	jmp  play_loop

_end_reached:
	; reset sound to beginning
	lda  #<pcm_data
	sta  pcm_ptr
	lda  #>pcm_data
	sta  pcm_ptr+1
	bra  _continue

stop_playback:
	; stop playback
	stz  VERA_AUDIO_RATE
	stz  VERA_AUDIO_CTRL
	ldx  #<audio_off_txt
	ldy  #>audio_off_txt
	jsr  print
	rts

audio_on_txt:	.text "AUDIO ON", $0d, $00
audio_off_txt:	.text "AUDIO OFF", $0d, $00

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
