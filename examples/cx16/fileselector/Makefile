.PHONY:  all clean zip run

all: main.prg fselector.bin standalone.prg

clean:
	rm -f main.asm main.vice* main.prg fselector.asm fselector.vice* fselector.bin standalone.asm standalone.vice* standalone.prg

fselector.bin: fselector.p8
	prog8c -target cx16 $<

standalone.prg: standalone.p8
	prog8c -target cx16 $<

main.prg: main.p8 fselector.bin
	prog8c -target cx16 $<

run: main.prg
	x16emu -scale 2 -run -prg main.prg
