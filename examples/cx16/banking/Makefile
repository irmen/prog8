.PHONY:  all clean zip run

all: PROGRAM.PRG

clean:
	rm -f program.asm program.vice* program.prg PROGRAM.PRG

PROGRAM.PRG: library1.asm library2.asm program.p8
	64tass --case-sensitive --ascii --long-branch library1.asm -o LIBRARY1.PRG
	64tass --case-sensitive --ascii --long-branch library2.asm -o LIBRARY2.PRG
	prog8c -target cx16 program.p8
	mv program.prg PROGRAM.PRG

run: PROGRAM.PRG
	x16emu -scale 2 -run -prg PROGRAM.PRG

