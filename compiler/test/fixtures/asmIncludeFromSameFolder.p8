%import textio
main {
	str myBar = "main.bar"
;foo_bar:
;    %asminclude "foo_bar.asm"   ; TODO: should be accessible from inside start() but give assembler error. See github issue #62
	sub start() {
	    txt.print(myBar)
	    txt.print(&foo_bar)
	    return
foo_bar:
        %asminclude "foo_bar.asm"
	}
}
