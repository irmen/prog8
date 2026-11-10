%import audio
%import textio

; Amiga high-level audio test plays sounds on all 4 channels

main {
    sub start() {
        txt.print("HL Audio test\n")

        txt.print("init audio...\n")
        if audio.init() {

            txt.print("\nplay 3 (L)...")
            audio.play(3, sample.data, sample.SAMPLE_LEN, 10000, 64, 4)
            txt.print("wait")
            audio.wait_channel(3)

            txt.print("\nplay 2 (R)...")
            audio.play(2, sample.data, sample.SAMPLE_LEN, 9000, 64, 3)
            txt.print("wait")
            audio.wait_channel(2)

            txt.print("\nplay 0 (L)...")
            audio.play(0, sample.data, sample.SAMPLE_LEN, 8000, 64, 2)
            txt.print("wait")
            audio.wait_channel(0)

            txt.print("\nplay 1 (R)...")
            audio.play(1, sample.data, sample.SAMPLE_LEN, 7000, 64, 1)
            txt.print("wait")
            audio.wait_channel(1)

            txt.print("\nClosing down.\n")
            audio.wait_all()
            audio.closedown()
        }
    }
}


sample {
    %option amiga_chipram

    const uword SAMPLE_LEN = 5290
    %align 2
data:
    %asmbinary "hlaudio-sample.bin"
}
