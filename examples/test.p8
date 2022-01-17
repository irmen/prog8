%import textio

main {
    str myBar = "main.bar"

foo_bar:
    %asm {{
        nop
    }}

	sub start() {
	    txt.print(myBar)
	    txt.print(&foo_bar)
	    return
	}
}
