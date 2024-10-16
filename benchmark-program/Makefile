.PHONY:  all clean emu

all:  benchmark.prg

clean:
	rm -f *.prg *.PRG *.asm *.vice-* *.BIN *.PAL *.zip *.7z

emu:  benchmark.prg
	x16emu -run -prg $< -warp

benchmark.prg: benchmark.p8 b_3d.p8 b_adpcm.p8 b_circles.p8 b_life.p8 b_mandelbrot.p8 b_maze.p8 b_queens.p8 b_textelite.p8
	prog8c $< -target cx16 
