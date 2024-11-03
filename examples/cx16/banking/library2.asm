.cpu  'w65c02'
.enc  'none'

* = $a000

CHROUT = $ffd2

message_pointer = $02

routine1:
	lda  #<message
	sta  message_pointer
	lda  #>message
	sta  message_pointer+1
	ldy  #0
-	lda  (message_pointer),y
	beq  +
	jsr  CHROUT
	iny
	bne  -
+	lda  #<44444
	ldy  #>44444
	rts

message:
	.text  "hello from routine 2",13,0
