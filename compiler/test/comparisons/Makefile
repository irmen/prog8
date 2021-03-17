.PHONY: all clean test

all: test

clean:
	rm -f *.prg *.asm *.vice-* test_*.p8

test: clean
	python make_tests.py
	p8compile -noopt -target cx16 *.p8 >/dev/null
	for program in *.prg; do \
		echo "RUNNING:" $$program ; \
		x16emu -run -prg $$program >/dev/null ; \
	done

