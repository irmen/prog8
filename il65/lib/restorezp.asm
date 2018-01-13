; backup/restore the zero page
; this is in a separate file so it can be omitted completely if it's not needed.

_il65_save_zeropage
		lda  #%00101111
		sta  _il65_zp_backup            ; default value for $00
		lda  #%00100111
		sta  _il65_zp_backup+1          ; default value for $01
		ldx  #2
-		lda  $00,x
		sta  _il65_zp_backup,x
		inx
		bne  -
		rts

_il65_restore_zeropage
		php
		pha
		txa
		pha
		sei

		lda  $a0		        ; save the current jiffy clock
		sta  _il65_zp_backup+$a0
		lda  $a1
		sta  _il65_zp_backup+$a1
		lda  $a2
		sta  _il65_zp_backup+$a2

		ldx  #0
-		lda  _il65_zp_backup,x
		sta  $00,x
		inx
		bne  -
		cli
		pla
		tax
		pla
		plp
		rts

_il65_zp_backup
		.fill  256
