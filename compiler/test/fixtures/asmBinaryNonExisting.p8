main {
	sub start() {
	    stuff.do_nothing()
	}   
}

stuff $1000 {
	extsub $1000 = do_nothing()
	%asmbinary "i_do_not_exist.bin", 0
}