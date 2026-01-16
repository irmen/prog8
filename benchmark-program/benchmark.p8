
;  This benchmark program is meant to check for regressions in the
;  Prog8 compiler's code-generator (performance wise).
;
;  As the X16 computer is a more or less fixed system, it's not very useful
;  to benchmark the computer itself with.


%import textio
%import b_adpcm
%import b_circles
%import b_3d
%import b_life
%import b_mandelbrot
%import b_queens
%import b_textelite
%import b_maze
%import b_sprites
%import b_btree

%zeropage basicsafe
%option no_sysinit


main {

    str[20] benchmark_names
    uword[20] benchmark_score
    str version = "12.1"


    sub start() {
        ubyte benchmark_number

        cx16.set_screen_mode(3)
        txt.color2(1, 6)
        txt.clear_screen()

        txt.print("\n\n\n  prog8 compiler benchmark tests.\n\n  benchmark version ")
        txt.print(version)
        txt.print("\n\n")
        sys.wait(120)

        benchmark_number = 0

        announce_benchmark("maze solver")
        benchmark_score[benchmark_number]  = maze.bench(300)
        benchmark_number++

        announce_benchmark("n-queens")
        benchmark_score[benchmark_number]  = queens.bench(300)
        benchmark_number++

        announce_benchmark("mandelbrot (floating point)")
        benchmark_score[benchmark_number]  = mandelbrot.calc(400)
        benchmark_number++

        announce_benchmark("game of life")
        benchmark_score[benchmark_number]  = life.benchmark(300)
        benchmark_number++

        announce_benchmark("3d model rotation")
        benchmark_score[benchmark_number]  = rotate3d.benchmark(300)
        benchmark_number++

        announce_benchmark("adpcm audio decoding")
        benchmark_score[benchmark_number]  = adpcm.decode_benchmark(300)
        benchmark_number++

        announce_benchmark("circles with gfx_lores")
        benchmark_score[benchmark_number]  = circles.draw(false, 400)
        benchmark_number++

        announce_benchmark("text-elite")
        benchmark_score[benchmark_number]  = textelite.bench(120)
        benchmark_number++

        announce_benchmark("sprites-coroutines-defer")
        benchmark_score[benchmark_number]  = animsprites.benchmark(300)
        benchmark_number++

        announce_benchmark("btree-struct-pointers")
        benchmark_score[benchmark_number]  = btree.benchmark(200)
        benchmark_number++

        benchmark_names[benchmark_number] = 0
        benchmark_score[benchmark_number] = 0

        cx16.set_screen_mode(3)
        txt.uppercase()
        txt.color2(1, 6)
        uword total_score
        benchmark_number = 0
        txt.print("\nscore benchmark (")
        txt.print(version)
        txt.print(")\n\n")

        do {
            txt.spc()
            txt.print_uw(benchmark_score[benchmark_number])
            txt.column(6)
            txt.print(benchmark_names[benchmark_number])
            total_score += benchmark_score[benchmark_number]
            txt.nl()
            benchmark_number++
        } until benchmark_names[benchmark_number]==0

        txt.print("\n\ntotal score : ")
        txt.print_uw(total_score)
        txt.print(" (higher=better)\n")

        sub announce_benchmark(str name) {
            benchmark_names[benchmark_number] = name
            cx16.set_screen_mode(3)
            txt.uppercase()
            txt.color2(1, 6)
            txt.clear_screen()
            txt.plot(4, 6)
            txt.print(benchmark_names[benchmark_number])
            txt.nl()
            sys.wait(60)
        }
    }
}
