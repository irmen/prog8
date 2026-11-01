main {
    sub start() {
        bool failed
        long size

        ; amiga500/OFS/68000
        failed,size = trackloader.load_OFS(1, "abc", 2000, 0, 1)

        ; amiga1200+/FFS/68020+
        failed,size = trackloader.load_FFS(1, "abc", 2000, 0, 2)
    }
}

trackloader {
    asmsub load_OFS(pointer loadaddress @A0, str filename @A1, pointer mfmbuffer @A2, pointer callback @A3, byte drive @D0) -> bool @Pz, long @D1 {
        ; OFS trackloader, 68000 compatible
        ; mfmbuffer must be $3200 bytes of word-aligned chip ram.
        ; callback can be 0 if you don't want it
        ; drive = (0,1,2,3)
        ;   bit7=1 use previous status (drive/motor), add #-128
        ;   bit6=1 no motor off at exit, add #+64
        ;   -3 seek to early head position (special), moveq #-3
        ;   (imply bit7=bit6=1, used for a clean exit to system)
        ; returns: error flag (Z set -> true means error!) and file length in d1.
        ; NOTE inverted polarity: the boolean is true when the load FAILED (Z=1->error)!

        %asminclude "ocean_loader.s"
    }

    asmsub load_FFS(pointer loadaddress @A0, str filename @A1, pointer mfmbuffer @A2, pointer callback @A3, byte drive @D0) -> bool @Pz, long @D1 {
        ; FFS trackloader, requires 68020+
        ; mfmbuffer must be $3200 bytes of word-aligned chip ram.
        ; callback can be 0 if you don't want it
        ; drive = (0,1,2,3)
        ;   bit7=1 use previous status (drive/motor), add #-128
        ;   bit6=1 no motor off at exit, add #+64
        ;   -3 seek to early head position (special), moveq #-3
        ;   (imply bit7=bit6=1, used for a clean exit to system)
        ; returns: error flag (Z set -> true means error!) and file length in d1.
        ; NOTE inverted polarity: the boolean is true when the load FAILED (Z=1->error)!

        %asminclude "ocean_loader_v2.s"
    }
}
