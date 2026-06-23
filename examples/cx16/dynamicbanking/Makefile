.PHONY: all clean run

all: program.prg

clean:
	rm -f *.asm *.vice* *.prg *.bin *.bankedcalls

library1.bin library2.bin library3.bin &: library1.p8 library2.p8 library3.p8
	prog8c -target cx16 library1.p8 library2.p8 library3.p8

program.prg: library1.bin library2.bin library3.bin program.p8
	prog8c -target cx16 program.p8

run: program.prg
	x16emu -scale 2 -run -prg program.prg
