%import diskio
%import textio
%import zsmkit_high
%memtop $8c00           ; zsmkit is loaded from 8c00 onwards

;; Proof Of Concept ZSM player using a binary blob version of zsmkit by MooingLemur
;; This version is a bit simpler as "demo1".
;; This one shows how you could integrate the zsmkit blob located in higher memory at $8c30.
;; It loads the blob separately. It also doesn't use streaming,
;; it simply loads the whole ZSM file into high memory (bank 2 onwards) at the start too.

main {
	const ubyte zsmkit_bank = 1

	sub start() {
		txt.print("zsmkit demo program (preloaded)\n")

		load_and_init_zsmkit(iso:"lib/zsmkit-8c00.bin")
		load_music_file(iso:"music/MUSIC.ZSM")

		play_music()
	}

	sub load_and_init_zsmkit(str blobname) {
	    if diskio.load_raw(blobname, $8c00)>0
		    zsmkit.zsm_init_engine(zsmkit_bank)
        else {
            txt.print("error loading zsmkit blob\n")
            sys.exit(1)
        }
	}

	sub load_music_file(str filename) {
	    cx16.rambank(zsmkit_bank+1)
	    if diskio.load_raw(filename, $a000)==0 {
	        txt.print("error loading music file\n")
	        sys.exit(1)
	    }
	}

	sub play_music() {
		cx16.rambank(zsmkit_bank+1)
		zsmkit.zsm_setmem(0, $a000)
		zsmkit.zsm_play(0)

		repeat {
			sys.waitvsync()
			zsmkit.zsm_tick(0)
			txt.chrout('.')
		}
	}
}
