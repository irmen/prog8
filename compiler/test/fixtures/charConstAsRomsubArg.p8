main {
	romsub $FFD2 = chrout(ubyte ch @ A)
	sub start() {
		const ubyte ch = '\n'
		chrout(ch)
	}
}
