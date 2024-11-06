main {
	sub start() {
	    stuff.do_nothing()
	}   
}

stuff $1000 {
	extsub $1000 = do_nothing()
	%asmbinary "do_nothing1.bin", 0
}