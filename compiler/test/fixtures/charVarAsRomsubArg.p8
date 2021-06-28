main {
	romsub $FFD2 = chrout(ubyte ch @ A)
	sub start() {
		ubyte ch = '\n'
		chrout(ch)
	}
}
