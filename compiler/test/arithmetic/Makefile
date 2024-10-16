.PHONY: all clean test

all: test

clean:
	rm -f *.prg *.asm *.vice-*

test: clean
	prog8c -target cx16 *.p8 >/dev/null
	for program in *.prg; do \
		echo "RUNNING:" $$program ; \
		x16emu -run -prg $$program >/dev/null ; \
	done
