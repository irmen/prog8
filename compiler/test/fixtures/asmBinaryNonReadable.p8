main {
	sub start() {
	    stuff.do_nothing()
	}   
}

stuff $1000 {
	romsub $1000 = do_nothing()
	%asmbinary "subFolder", 0
}