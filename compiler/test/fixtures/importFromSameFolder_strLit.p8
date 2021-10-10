%import textio
%import "foo_bar.p8"
main {
	str myBar = "main.bar"
	sub start() {
	    txt.print(myBar)
	    txt.print(foo.bar)
	}
}
