%import textio

main {
    str myBar = "main.bar"

foo_bar:
    ; %asminclude "compiler/test/fixtures/foo_bar.asm22"   ; FIXME: should be accessible from inside start() but give assembler error

	sub start() {
	    txt.print(myBar)
	    txt.print(&foo_bar)
	    return
	}
}
