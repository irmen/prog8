%import textio
%import diskio
%import palette
%import zsmkit
%zeropage basicsafe

;; Proof Of Concept ZSM player using zsmkit v2 by MooingLemur
;; zsmkit bank is hardcoded in the module import above
main {
	ubyte[255] zsmkit_lowram

	sub start() {
	    ; load zsmkit in bank 1 and the music starting from bank 2 onwards.
		cx16.rambank(zsmkit.ZSMKitBank)
		void diskio.load_raw("zsmkit-a000.bin",$A000)
		cx16.rambank(2)
		void diskio.load_raw("music.zsm",$A000)
		;;void diskio.load_raw("song2.zsm",$A000)

		zsmkit.zsm_init_engine(&zsmkit_lowram)

		setup_isr()
		play_music()
	}

	sub setup_isr() {
		;; You could use zsmkit.zsmkit_setisr() and be done with it
		;; but here's an example of a custom ISR.
		;; Note that jsrfar is unsafe to call in a handler, so the ISR
		;; uses zsmkit.zsm_tick_isr() with a manual bank change
		sys.set_irq(&irq.handler)
	}

	sub play_music() {
		uword zsmptr
		ubyte zsmbank

		txt.cls()

		zsmkit.zsm_setbank(0, 2)
		zsmkit.zsm_setmem(0, $A000)

		zsmkit.zsm_play(0)
		repeat {
			sys.waitvsync()
			void, zsmptr, zsmbank = zsmkit.zsm_getptr(0)
			txt.home()
			txt.print_ubhex(zsmbank, true)
			txt.print(":")
			txt.print_uwhex(zsmptr, false)
		}
	}
}

irq {
	sub handler() -> bool {
		ubyte savebank

		savebank = cx16.getrambank()
		cx16.rambank(zsmkit.ZSMKitBank)
		zsmkit.zsm_tick_isr(0) ; NOTE that zsm_tick() is not allowed in a handler
		cx16.rambank(savebank)

		return true
	}
}
