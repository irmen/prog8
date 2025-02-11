.PHONY:  all clean

all: main-c64.prg main-cx16.prg main-pet.prg atari-hello.xex atari-fibonacci.xex neo-hello.bin

clean:
	rm -f *.prg *.PRG *.xex *.bin *.asm *.vice-*

main-c64.prg: src/main.p8 targetconfigs/tinyc64.properties
	prog8c -target targetconfigs/tinyc64.properties src/main.p8 -sourcelines
	mv main.prg $@

main-cx16.prg: src/main.p8 targetconfigs/tinycx16.properties
	prog8c -target targetconfigs/tinycx16.properties src/main.p8 -sourcelines
	mv main.prg $@

main-pet.prg: src/main.p8 targetconfigs/tinypet.properties
	prog8c -target targetconfigs/tinypet.properties src/main.p8 -sourcelines
	mv main.prg $@

atari-hello.xex: src/atari-hello.p8
	prog8c -target targetconfigs/atari.properties src/atari-hello.p8

atari-fibonacci.xex: src/atari-fibonacci.p8
	prog8c -target targetconfigs/atari.properties src/atari-fibonacci.p8

neo-hello.bin: src/neo-hello.p8
	prog8c -target targetconfigs/neo6502.properties src/neo-hello.p8

run-atari-hello:
	prog8c -target targetconfigs/atari.properties src/atari-hello.p8 -emu

run-atari-fibonacci:
	prog8c -target targetconfigs/atari.properties src/atari-fibonacci.p8 -emu

run-neo-hello:
	prog8c -target targetconfigs/neo6502.properties src/neo-hello.p8 -emu

