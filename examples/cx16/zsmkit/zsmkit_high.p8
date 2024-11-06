
; extubs for zsmkit loaded at $8c00

zsmkit {
	extsub $8C00 = zsm_init_engine(ubyte bank @A) clobbers(A, X, Y)
	extsub $8C03 = zsm_tick(ubyte type @A) clobbers(A, X, Y)

	extsub $8C06 = zsm_play(ubyte prio @X) clobbers(A, X, Y)
	extsub $8C09 = zsm_stop(ubyte prio @X) clobbers(A, X, Y)
	extsub $8C0C = zsm_rewind(ubyte prio @X) clobbers(A, X, Y)
	extsub $8C0F = zsm_close(ubyte prio @X) clobbers(A, X, Y)
	extsub $8C12 = zsm_fill_buffers() clobbers(A, X, Y)
	extsub $8C15 = zsm_setlfs(ubyte prio @X, ubyte lfn_sa @A, ubyte device @Y) clobbers(A, X, Y)
	extsub $8C18 = zsm_setfile(ubyte prio @X, str filename @AY) clobbers(A, X, Y)
	extsub $8C1B = zsm_loadpcm(ubyte prio @X, uword data_ptr @AY) clobbers(X) -> uword @AY
	extsub $8C1E = zsm_setmem(ubyte prio @X, uword data_ptr @AY) clobbers(A, X, Y)
	extsub $8C21 = zsm_setatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub $8C24 = zsm_setcb(ubyte prio @X, uword func_ptr @AY) clobbers(A, X, Y)
	extsub $8C27 = zsm_clearcb(ubyte prio @X) clobbers(A, X, Y)
	extsub $8C2A = zsm_getstate(ubyte prio @X) clobbers(X) -> bool @Pc, bool @Pz, uword @AY
	extsub $8C2D = zsm_setrate(ubyte prio @X, uword rate @AY) clobbers(A, X, Y)
	extsub $8C30 = zsm_getrate(ubyte prio @X) clobbers() -> uword @AY
	extsub $8C33 = zsm_setloop(ubyte prio @X, bool loop @Pc) clobbers(A, X, Y)
	extsub $8C36 = zsm_opmatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub $8C39 = zsm_psgatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub $8C3C = zsm_pcmatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub $8C3F = zsm_set_int_rate(ubyte value @A, ubyte frac @Y) clobbers(A, X, Y)

	extsub $8C4B = zcm_setmem(ubyte slot @X, uword data_ptr @AY) clobbers(A)
	extsub $8C4E = zcm_play(ubyte slot @X, ubyte volume @A) clobbers(A, X)
	extsub $8C51 = zcm_stop() clobbers(A)

	extsub $8C54 = zsmkit_setisr() clobbers(A)
	extsub $8C57 = zsmkit_clearisr() clobbers(A)
}
