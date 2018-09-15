%zeropage full
%address 33

~ extra3  {
	; this is imported

	X = 42
	return 44
}

~ extra233  {
	; this is imported

    const byte snerp=33
    const byte snerp2 = snerp+22

	X = 42+snerp
	;return 44+snerp
	return

	    sub foo234234() -> () {
          A=99+snerp
          ;return A+snerp2
          return
        }

    sub thingy()->(X) {
        ;return 99
        return
    }
}


~ mainzzz  {
	; this is imported

	X = 42
	;return 44
	return
}

