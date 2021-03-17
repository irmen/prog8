.PHONY: all clean test

all: test

clean:
	rm -f *.prg *.asm *.vice-*

test: clean
	p8compile *.p8 >/dev/null
	for program in *.prg; do \
		echo "RUNNING:" $$program ; \
		x64sc $$program >/dev/null ; \
	done
