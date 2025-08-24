%option no_sysinit
%zeropage kernalsafe
%import textio
%import compression
%import math
%import sorting
%import strings
%import diskio

main {
    sub start() {
;        test_compression()
;        test_sorting1()
;        test_sorting2()
;        test_math()
;        test_syslib()
;        test_strings()
;        test_conv()
        test_diskio()
;        test_textio()

        repeat {}
    }

    sub test_diskio() {
        txt.print("--diskio--\n")
        sys.memset(target, len(target), 0)
        diskio.delete("derp.bin")
        void diskio.f_open_w("derp.bin")
        repeat 12
            void diskio.f_write("derpderp123", 11)
        diskio.f_close_w()

        void diskio.f_open("derp.bin")
        diskio.f_read(target, 60)
        txt.print(target)
        txt.nl()
    }

    ubyte[100] target

    sub test_conv() {
        txt.print("--conv--\n")
        txt.print_b(-111)
        txt.spc()
        txt.print_ub(222)
        txt.spc()
        txt.print_uw(22222)
        txt.spc()
        txt.print_w(-22222)
        txt.nl()
        txt.print_ubbin(222, true)
        txt.spc()
        txt.print_ubhex(222, true)
        txt.spc()
        txt.print_uwbin(2222, true)
        txt.spc()
        txt.print_uwhex(2222, true)
        txt.nl()
        txt.print_ub0(1)
        txt.spc()
        txt.print_uw0(123)
        txt.nl()
    }

    sub test_strings() {
        txt.print("--strings--\n")
        ubyte idx
        bool found
        idx, found = strings.rfind(source, '1')
        txt.print_ub(idx)
        txt.nl()
    }

    sub test_textio() {
        txt.print("--textio--\n")
        txt.print("enter some input: ")
        void txt.input_chars(&target)
        txt.print(target)
        txt.nl()
    }

    sub test_syslib() {
        txt.print("--syslib--\n")
        sys.internal_stringcopy(source, target)
        txt.print(target)
        txt.nl()
        sys.memset(target, sizeof(target), 0)
        txt.print(target)
        txt.nl()
        sys.memcopy(source, target, len(source))
        txt.print(target)
        txt.nl()
        sys.memsetw(&target as ^^uword, 20, $5051)
        txt.print(target)
        txt.nl()
        txt.print_b(sys.memcmp(source, target, len(source)))
        txt.nl()
    }

    sub test_sorting1() {
        txt.print("--sorting (shell)--\n")
        ubyte[] bytes1 = [77,33,44,99,11,55]
        ubyte[] bytes2 = [77,33,44,99,11,55]
        uword[] @nosplit values1 = [1,2,3,4,5,6]
        uword[] @nosplit words1 = [777,333,444,999,111,555]
        uword[] @nosplit words2 = [777,333,444,999,111,555]
        uword[] @nosplit values2 = [1,2,3,4,5,6]
        sorting.shellsort_ub(&bytes1, len(bytes1))
        sorting.shellsort_by_ub(&bytes2, &values1, len(bytes2))
        sorting.shellsort_uw(&words1, len(words1))
        sorting.shellsort_by_uw(&words2, &values2, len(words2))

        for cx16.r0L in bytes1 {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
        txt.nl()
        for cx16.r0L in bytes2 {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in values1 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in words1 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in words2 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in values2 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()

    }

    sub test_sorting2() {
        txt.print("--sorting (gnome)--\n")
        ubyte[] bytes1 = [77,33,44,99,11,55]
        ubyte[] bytes2 = [77,33,44,99,11,55]
        uword[] @nosplit values1 = [1,2,3,4,5,6]
        uword[] @nosplit words1 = [777,333,444,999,111,555]
        uword[] @nosplit words2 = [777,333,444,999,111,555]
        uword[] @nosplit values2 = [1,2,3,4,5,6]
        sorting.gnomesort_ub(&bytes1, len(bytes1))
        sorting.gnomesort_by_ub(&bytes2, &values1, len(bytes2))
        sorting.gnomesort_uw(&words1, len(words1))
        sorting.gnomesort_by_uw(&words2, &values2, len(words2))

        for cx16.r0L in bytes1 {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
        txt.nl()
        for cx16.r0L in bytes2 {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in values1 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in words1 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in words2 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        for cx16.r0 in values2 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()

    }

    sub test_math() {
        txt.print("--math--\n")
        txt.print("expected 15567: ")
        txt.print_uw(math.crc16(source, len(source)))
        txt.print("\nexpected 8747,54089: ")
        math.crc32(source, len(source))
        txt.print_uw(cx16.r14)
        txt.chrout(',')
        txt.print_uw(cx16.r15)
        txt.nl()
    }

    str source = petscii:"Lorem ipsuuuuuuuuuuuum dollllllllllllllloooooooor sit ametttttttttttttttt, cccccccccccccccconsecteeeeetuuuuuur aaaaaaaaa111111222222333333444444"

    sub test_compression() {
        txt.print("--compression--\n")

        ubyte[256] compressed
        ubyte[256] decompressed

        txt.print_uw(len(source))
        txt.nl()

        uword size = compression.encode_rle(source, len(source), compressed, true)
        txt.print_uw(size)
        txt.nl()

        size = compression.decode_rle(compressed, decompressed, sizeof(decompressed))
        txt.print_uw(size)
        txt.nl()
        txt.print(source)
        txt.nl()
        txt.print(decompressed)
        txt.nl()
    }
}
