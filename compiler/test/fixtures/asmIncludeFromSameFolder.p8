%import textio
main {
	str myBar = "main.bar"
;foo_bar:
;    %asminclude "foo_bar.asm"   ; FIXME: should be accessible from inside start() but give assembler error
	sub start() {
	    txt.print(myBar)
	    txt.print(&foo_bar)
	    return
foo_bar:
        %asminclude "foo_bar.asm"
	}
}
