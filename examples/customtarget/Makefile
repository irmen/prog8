.PHONY:  all clean

all: main-c64.prg main-cx16.prg main-pet.prg

clean:
	rm -f *.prg *.PRG *.asm *.vice-*

main-c64.prg: main.p8 tinyc64.properties
	prog8c -target ./tinyc64.properties main.p8 -sourcelines
	mv main.prg $@

main-cx16.prg: main.p8 tinycx16.properties
	prog8c -target ./tinycx16.properties main.p8 -sourcelines
	mv main.prg $@

main-pet.prg: main.p8 tinypet.properties
	prog8c -target ./tinypet.properties main.p8 -sourcelines
	mv main.prg $@
