%import textio
%zeropage basicsafe
%option no_sysinit
; simple line comment; consecutive lines can be folded
; TODO: something
; FIXME #31
main {
	str   input = "string literal\r\n\"\\"
    bool bb = false
	ubyte c = 'x' ; character literal in bold
	ubyte decimal = 0 + 1 - 2 * 3
	float pi = 3.1415
	ubyte boolean = true or false and true xor not false
	str   temp  = "?"
	word[] numbers = [$80ea, %0101011, 23]
	inline asmsub foo(ubyte char @A) clobbers(Y) {
		asm {{
			a_label:
						nop			; comment inside asm
						bcc _done
						sec
			_done:		rts
		}} 
	}
	inline sub start(ubyte char @A) -> void {
		ubyte @zp ch = min(max, n, (x, 5))
		if (true) {
			goto nirvana
		} else {
			return 0
		}
		repeat {
			ch = input[index+5]
			when ch {
				0 -> {
					break
				}
				else -> {
					temp[0] = ch
					txt.print(temp) ; wrongly optimized to
					;      lda #$3f
					;      jsr txt.chrout

					; with -noopt the above line is correctly turned into
					;      ldy  #>temp
					;      ldy  #<temp
					;      jsr  txt.print
				}
			}
			index++
		}
		return
	}
}
