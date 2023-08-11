.PHONY: all clean test

all: test

clean:
	rm -f *.prg *.asm *.vice-* test_*.p8

test: clean generate test_prgs

generate:
	python make_tests.py
	p8compile -target cx16 *.p8 >/dev/null

test_prgs:
	x16emu -run -prg more_compares.prg
	for program in *.prg; do \
		echo "RUNNING:" $$program ; \
		x16emu -run -prg $$program >/dev/null ; \
	done
