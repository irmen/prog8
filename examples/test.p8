%import textio
%import string
%import compression
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[] rle = [
            5, '1', '2', '3', '4', '5', '6',
            3, 'a', 'b', 'c', 'd',
            0, 'z',
            3, '1', '2', '3','4',
            -5, '=',
            -127, '+',
            4, '!', '@', '#', '$', '%',
            128]

        str @shared result = "\x00"*200

        uword ptr = &rle

        txt.print_uw(compression.decode_rle_srcfunc(callback, result, len(result)))
        txt.nl()
        txt.print_uw(compression.decode_rle(rle, result, len(result)))
        txt.nl()
        txt.print_uw(string.length(result))
        txt.nl()
        txt.print(result)
        txt.nl()
        txt.print_uwhex(&result, true)
        txt.nl()

        sub callback() -> ubyte {
            ubyte x = @(ptr)
            ptr++
            return x
        }


        ubyte[256] buffer
        ubyte[256] buffer2
        uword bufptr = &buffer
        uword outputsize=0

        sub outputter(ubyte value) {
            @(bufptr) = value
            bufptr++
            outputsize++
        }

        str input = iso:"123456aaaaabcdzzz1234======++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++!@#$%"
        ubyte[31] expected_rle = [$5, $31, $32, $33, $34, $35, $36, $fc, $61, $2, $62, $63, $64, $fe, $7a, $3, $31, $32, $33, $34, $fb, $3d, $81, $2b, $4, $21, $40, $23, $24, $25, $80]
        txt.print(input)
        txt.nl()

        compression.encode_rle_outfunc(input, len(input), outputter, true)
        txt.print_uw(outputsize)
        txt.nl()
        if outputsize!=len(expected_rle)
            txt.print("wrong rle size!\n")

        txt.print("\ncompare rle (encode using callback):\n")
        for cx16.r9L in 0 to len(expected_rle)-1 {
;            txt.print_ub0(cx16.r9L)
;            txt.spc()
;            txt.print_ubhex(expected_rle[cx16.r9L], false)
;            txt.spc()
;            txt.print_ubhex(buffer[cx16.r9L], false)
            if buffer[cx16.r9L] != expected_rle[cx16.r9L]
                txt.print(" wrong rle data!")
;            txt.nl()
        }
        txt.nl()

        cx16.r0 = compression.decode_rle(buffer, buffer2, len(buffer2))
        if cx16.r0 != len(input)
            txt.print("wrong decompress result!\n")
        else {
            txt.print("good result: ")
            txt.print(buffer2)
            txt.nl()
        }


        buffer[0] = buffer[1] = buffer[2] = 128
        outputsize = compression.encode_rle(input, len(input), buffer, true)
        txt.print_uw(outputsize)
        txt.nl()
        if outputsize!=len(expected_rle)
            txt.print("wrong rle size!\n")

        txt.print("\ncompare rle (encode into buffer):\n")
        for cx16.r9L in 0 to len(expected_rle)-1 {
;            txt.print_ub0(cx16.r9L)
;            txt.spc()
;            txt.print_ubhex(expected_rle[cx16.r9L], false)
;            txt.spc()
;            txt.print_ubhex(buffer[cx16.r9L], false)
            if buffer[cx16.r9L] != expected_rle[cx16.r9L]
                txt.print(" wrong rle data!")
;            txt.nl()
        }
        txt.nl()

        cx16.r0 = compression.decode_rle(buffer, buffer2, len(buffer2))
        if cx16.r0 != len(input)
            txt.print("wrong decompress result!\n")
        else {
            txt.print("good result: ")
            txt.print(buffer2)
            txt.nl()
        }
    }
}

