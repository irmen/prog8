.PHONY:  all clean zip run

all: main.prg lib-6000.bin

clean:
	rm -f main.asm main.vice* main.prg thelibrary*.bin thelibrary*.asm thelibrary.vice* lib-6000.bin

lib-6000.bin: thelibrary.p8
	prog8c -target c64 $<
	mv thelibrary.bin lib-6000.bin

main.prg: main.p8 lib-6000.bin
	prog8c -target c64 $<

run: main.prg
	x64sc main.prg
