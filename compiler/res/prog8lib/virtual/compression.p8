compression {

    sub decode_rle(^^ubyte @zp compressed, ^^ubyte @zp target, uword maxsize) -> uword {
        cx16.r0 = target    ; original target
        cx16.r1 = target+maxsize     ; decompression limit

        while target<cx16.r1 {
            cx16.r2L = @(compressed)
            if_neg {
                if cx16.r2L==128
                    break
                ; replicate the next byte -n+1 times
                compressed++
                cx16.r3L = @(compressed)
                repeat 2+(cx16.r2L^255) {
                    @(target) = cx16.r3L
                    target++
                }
                compressed++
            } else {
                ; copy the next n+1 bytes
                compressed++
                repeat cx16.r2L+1 {
                    @(target) = @(compressed)
                    compressed++
                    target++
                }
            }
        }
        return target-cx16.r0
    }

    sub encode_rle(^^ubyte data, uword size, ^^ubyte target, bool is_last_block) -> uword {
        ; -- Compress the given data block using ByteRun1 aka PackBits RLE encoding.
        ;    Returns the size of the compressed RLE data. Worst case result storage size needed = (size + (size+126) / 127) + 1.
        ;    is_last_block = usually true, but you can set it to false if you want to concatenate multiple
        ;                    compressed blocks (for instance if the source data is >64Kb)
        ;    This routine is not optimized for speed but for readability and ease of use.
        uword idx = 0
        uword literals_start_idx = 0
        ubyte literals_length = 0
        ^^ubyte orig_target = target

        sub next_same_span() {
            ; returns length in cx16.r1L, and the byte value in cx16.r1H
            cx16.r1H = data[idx]
            cx16.r1L = 0
            while data[idx]==cx16.r1H and cx16.r1L<128 and idx<size {
                idx++
                cx16.r1L++
            }
        }

        sub output_literals() {
            @(target) = literals_length-1
            target++
            ^^ubyte dataptr = data + literals_start_idx
            ubyte i
            for i in 0 to literals_length-1 {
                @(target) = @(dataptr)
                target++
                dataptr++
            }
            literals_length = 0
        }

        while idx<size {
            next_same_span()     ; count in r1L, value in r1H
            if cx16.r1L>1 {
                ; a replicate run
                if literals_length>0
                    output_literals()
                @(target) = (cx16.r1L^255)+2        ;  257-cx16.r1L
                target++
                @(target) = cx16.r1H
                target++
            }
            else {
                ; add more to the literals run
                if literals_length==128
                    output_literals()
                if literals_length==0
                    literals_start_idx = idx-1
                literals_length++
            }
        }

        if literals_length>0
            output_literals()

        if is_last_block {
            @(target) = 128
            target ++
        }

        return target-orig_target
    }

}
