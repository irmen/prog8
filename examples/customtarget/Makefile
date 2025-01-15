.PHONY:  all clean

all: main.prg

clean:
	rm -f *.prg *.PRG *.asm *.vice-*

main.prg: main.p8
	prog8c -target ./target.properties -srcdirs libraries main.p8 -sourcelines
