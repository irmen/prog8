.PHONY: all clean test

all: test

clean:
	rm -f *.prg *.p8ir *.asm *.vice-* test_*.p8

test: clean generate test_prgs

generate:
	python make_eq_tests.py
	python make_cmp_tests.py
	p8compile -target cx16 -sourcelines *.p8 >/dev/null

test_prgs:
	x16emu -run -prg ifelse.prg
	x16emu -run -prg optimized_compares.prg
	x16emu -run -prg more_compares.prg
	for program in test*.prg; do \
		echo "RUNNING:" $$program ; \
		x16emu -run -prg $$program >/dev/null ; \
	done
