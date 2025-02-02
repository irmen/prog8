.PHONY:  all clean zip run

all: main.prg fselector.bin standalone.prg

clean:
	rm -f main.asm main.vice* main.prg fselector.asm fselector.vice* fselector*.bin standalone.asm standalone.vice* standalone.prg

fselector-a000.bin: fselector.p8
	prog8c -target cx16 $<
	mv fselector.bin fselector-a000.bin

standalone.prg: standalone.p8
	prog8c -target cx16 $<

main.prg: main.p8 fselector-a000.bin
	prog8c -target cx16 $<

run: main.prg
	x16emu -scale 2 -run -prg main.prg
