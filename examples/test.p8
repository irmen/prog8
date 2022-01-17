%import textio

main {
    str myBar = "main.bar"

foo_bar:
    %asm {{
        nop
    }}

	sub start() {
	    txt.print(myBar)

	    %breakpoint

	    txt.print_uwhex(&foo_bar, true)

	    %breakpoint
	    return
	}
}
