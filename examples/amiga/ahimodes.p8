%import ahi
%import textio

; Prog8 port of the AHI developer-kit example Low-level/ScanAudioModes.
; Walks the audio-mode database and prints every mode with its properties.
; This is what you run to find out which audio modes your AHI drivers provide.
; It only reads the database, so it opens ahi.device without claiming a unit.

main {
    const ubyte STRLEN = 80

    long[58] tags
    long[8] etags
    uword tagn
    ubyte[STRLEN] name
    ubyte[STRLEN] driver
    ubyte[STRLEN] author
    ubyte[STRLEN] copyright
    ubyte[STRLEN] version
    ubyte[STRLEN] annotation
    ubyte[STRLEN] endpoint
    long freqs
    long volume
    long stereo
    long panning
    long hifi
    long pingpong
    long record
    long realtime
    long fullduplex
    long bits
    long channels
    long minmix
    long maxmix
    long inputs
    long outputs
    long minmon
    long maxmon
    long mingain
    long maxgain
    long minout
    long maxout

    sub start() {
        ; No unit: this only needs the mode database, not a hardware mixer.
        if not ahi.open(ahi.AHI_NO_UNIT) {
            txt.print("cannot open ahi.device\n")
            sys.exit(1)
        }

        long id = ahi.AHI_INVALID_ID
        repeat {
            id = ahi.NextAudioID(id)
            if id==ahi.AHI_INVALID_ID
                break
            show_mode(id)
        }

        txt.print("no more modes in the audio database\n")
        ahi.close()
    }

    sub show_mode(long id) {
        txt.print("\nMode ")
        txt.print_ulhex(id, true)
        txt.print("\n")
        if not query_mode(id)
            return

        txt.spc()
        txt.print("Name: \"")
        print_str(&name)
        txt.print("\"   Driver: \"DEVS:AHI/")
        print_str(&driver)
        txt.print(".audio\"\n")
        if author[0]!=0 {
            txt.spc()
            txt.print("Driver programmed by: ")
            print_str(&author)
            txt.print("\n")
        }
        if copyright[0]!=0 {
            txt.spc()
            txt.print("Copyright: ")
            print_str(&copyright)
            txt.print("\n")
        }
        if version[0]!=0 {
            txt.spc()
            txt.print("Version: ")
            print_str(&version)
            txt.print("\n")
        }
        if annotation[0]!=0 {
            txt.spc()
            print_str(&annotation)
            txt.print("\n")
        }

        txt.spc()
        txt.print("The hardware has ")
        txt.print_l(outputs)
        txt.print(" output(s)")
        show_endpoints(id, outputs, ahi.AHIDB_OutputArg, ahi.AHIDB_Output)
        txt.print(" and ")
        txt.print_l(inputs)
        txt.print(" input(s)")
        show_endpoints(id, inputs, ahi.AHIDB_InputArg, ahi.AHIDB_Input)
        txt.print(".\n")

        if minmon==maxmon {
            txt.spc()
            txt.print("No input monitor.\n")
        } else {
            txt.spc()
            txt.print("Input monitor volume range: ")
            print_percent(minmon)
            txt.print("-")
            print_percent(maxmon)
            txt.print(".\n")
        }
        if mingain==maxgain {
            txt.spc()
            txt.print("No input gain.\n")
        } else {
            txt.spc()
            txt.print("Input gain range: ")
            print_percent(mingain)
            txt.print("-")
            print_percent(maxgain)
            txt.print(".\n")
        }
        if minout==maxout {
            txt.spc()
            txt.print("No volume control.\n")
        } else {
            txt.spc()
            txt.print("Output volume range: ")
            print_percent(minout)
            txt.print("-")
            print_percent(maxout)
            txt.print(".\n")
        }

        txt.spc()
        if stereo!=0 {
            txt.print("Stereo output")
            if panning!=0
                txt.print(" (with panning)")
        } else {
            txt.print("Mono output")
        }
        txt.print(" in ")
        txt.print_l(bits)
        txt.print(" bits.\n")

        txt.spc()
        txt.print("Can play samples on max ")
        txt.print_l(channels)
        txt.print(" channels at ")
        if volume!=0
            txt.print("any volume")
        else
            txt.print("full volume only")
        txt.print(", ")
        if pingpong!=0
            txt.print("both forwards and backwards.\n")
        else
            txt.print("forwards only.\n")

        txt.spc()
        if hifi!=0
            txt.print("The mixing is non-destructive.\n")
        else
            txt.print("The mixing may be destructive.\n")

        txt.spc()
        txt.print_l(freqs)
        txt.print(" mixing frequencies between ")
        txt.print_l(minmix)
        txt.print(" and ")
        txt.print_l(maxmix)
        txt.print(" are available.\n")

        txt.spc()
        if record!=0 {
            txt.print("Recording is supported (")
            if fullduplex!=0
                txt.print("Full")
            else
                txt.print("Half")
            txt.print(" duplex).\n")
        } else {
            txt.print("Recording is not supported.\n")
        }

        txt.spc()
        if realtime!=0
            txt.print("This is a realtime mode.\n")
        else
            txt.print("This is a non-realtime mode.\n")
        txt.print("\n")
    }

    sub query_mode(long id) -> bool {
        ; Ask the device to fill all the variables above in one go.
        tagn = 0
        name[0] = 0
        driver[0] = 0
        author[0] = 0
        copyright[0] = 0
        version[0] = 0
        annotation[0] = 0
        freqs = 0
        volume = 0
        stereo = 0
        panning = 0
        hifi = 0
        pingpong = 0
        record = 0
        realtime = 0
        fullduplex = 0
        bits = 0
        channels = 0
        minmix = 0
        maxmix = 0
        inputs = 0
        outputs = 0
        minmon = 0
        maxmon = 0
        mingain = 0
        maxgain = 0
        minout = 0
        maxout = 0

        add_tag(ahi.AHIDB_BufferLen, STRLEN)
        add_tag(ahi.AHIDB_Frequencies, &freqs as long)
        add_tag(ahi.AHIDB_Volume, &volume as long)
        add_tag(ahi.AHIDB_Stereo, &stereo as long)
        add_tag(ahi.AHIDB_Panning, &panning as long)
        add_tag(ahi.AHIDB_HiFi, &hifi as long)
        add_tag(ahi.AHIDB_PingPong, &pingpong as long)
        add_tag(ahi.AHIDB_Record, &record as long)
        add_tag(ahi.AHIDB_FullDuplex, &fullduplex as long)
        add_tag(ahi.AHIDB_Realtime, &realtime as long)
        add_tag(ahi.AHIDB_Name, &name as long)
        add_tag(ahi.AHIDB_Driver, &driver as long)
        add_tag(ahi.AHIDB_Bits, &bits as long)
        add_tag(ahi.AHIDB_MaxChannels, &channels as long)
        add_tag(ahi.AHIDB_MinMixFreq, &minmix as long)
        add_tag(ahi.AHIDB_MaxMixFreq, &maxmix as long)
        add_tag(ahi.AHIDB_Author, &author as long)
        add_tag(ahi.AHIDB_Copyright, &copyright as long)
        add_tag(ahi.AHIDB_Version, &version as long)
        add_tag(ahi.AHIDB_Annotation, &annotation as long)
        add_tag(ahi.AHIDB_MinMonitorVolume, &minmon as long)
        add_tag(ahi.AHIDB_MaxMonitorVolume, &maxmon as long)
        add_tag(ahi.AHIDB_MinInputGain, &mingain as long)
        add_tag(ahi.AHIDB_MaxInputGain, &maxgain as long)
        add_tag(ahi.AHIDB_MinOutputVolume, &minout as long)
        add_tag(ahi.AHIDB_MaxOutputVolume, &maxout as long)
        add_tag(ahi.AHIDB_Inputs, &inputs as long)
        add_tag(ahi.AHIDB_Outputs, &outputs as long)
        add_tag(0, 0)      ; TAG_END
        return ahi.GetAudioAttrsA(id, 0 as pointer, &tags)!=0
    }

    sub show_endpoints(long id, long count, long argtag, long desctag) {
        if count==0
            return
        txt.print(":")
        long i = 0
        while i<count {
            endpoint[0] = 0
            etags[0] = ahi.AHIDB_BufferLen
            etags[1] = STRLEN
            etags[2] = argtag
            etags[3] = i
            etags[4] = desctag
            etags[5] = &endpoint as long
            etags[6] = 0
            etags[7] = 0
            void ahi.GetAudioAttrsA(id, 0 as pointer, &etags)
            txt.spc()
            print_str(&endpoint)
            i++
        }
    }

    sub add_tag(long tagval, long data) {
        ; Append one {tag, data} pair to the taglist being built.
        tags[tagn] = tagval
        tags[tagn+1] = data
        tagn = tagn+2
    }

    sub print_percent(long fixed) {
        ; Print a 16.16 fixed-point value as a whole percentage.
        txt.print_l((fixed*100)>>16)
        txt.print("%")
    }

    sub print_str(pointer buf) {
        ; Print a NUL-terminated string written into memory by the device.
        uword i = 0
        ubyte c = buf[i]
        while c!=0 and c!=13 and c!=10 {
            txt.chrout(c)
            i++
            c = buf[i]
        }
    }
}
