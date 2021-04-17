%import textio
%zeropage dontuse

main {
    uword hash_buckets = memory("buckets", 128*32*2)

    sub start()  {
        txt.print_uwhex(hash_buckets,true)
        txt.print("ok")
    }
}
