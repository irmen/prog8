%import textio
main {
	str myBar = "main.bar"

foo_bar:
    %asminclude "foo_bar.asm"

	sub start() {
	    txt.print(myBar)
	    txt.print(&foo_bar)
	    return

foo_bar_inside_start:
        %asminclude "foo_bar.asm"
	}
}
