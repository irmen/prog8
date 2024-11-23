
; extsubs for zsmkit loaded at $0830

zsmkit {
    const uword zsmjmp = $0830
	extsub zsmjmp + 0*3 = zsm_init_engine(ubyte bank @A) clobbers(A, X, Y)
	extsub zsmjmp + 1*3 = zsm_tick(ubyte type @A) clobbers(A, X, Y)

	extsub zsmjmp + 2*3 = zsm_play(ubyte prio @X) clobbers(A, X, Y)
	extsub zsmjmp + 3*3 = zsm_stop(ubyte prio @X) clobbers(A, X, Y)
	extsub zsmjmp + 4*3 = zsm_rewind(ubyte prio @X) clobbers(A, X, Y)
	extsub zsmjmp + 5*3 = zsm_close(ubyte prio @X) clobbers(A, X, Y)
	extsub zsmjmp + 6*3 = zsm_fill_buffers() clobbers(A, X, Y)
	extsub zsmjmp + 7*3 = zsm_setlfs(ubyte prio @X, ubyte lfn_sa @A, ubyte device @Y) clobbers(A, X, Y)
	extsub zsmjmp + 8*3 = zsm_setfile(ubyte prio @X, str filename @AY) clobbers(A, X, Y)
	extsub zsmjmp + 9*3 = zsm_loadpcm(ubyte prio @X, uword data_ptr @AY) clobbers(X) -> uword @AY
	extsub zsmjmp + 10*3 = zsm_setmem(ubyte prio @X, uword data_ptr @AY) clobbers(A, X, Y)
	extsub zsmjmp + 11*3 = zsm_setatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub zsmjmp + 12*3 = zsm_setcb(ubyte prio @X, uword func_ptr @AY) clobbers(A, X, Y)
	extsub zsmjmp + 13*3 = zsm_clearcb(ubyte prio @X) clobbers(A, X, Y)
	extsub zsmjmp + 14*3 = zsm_getstate(ubyte prio @X) clobbers(X) -> bool @Pc, bool @Pz, uword @AY
	extsub zsmjmp + 15*3 = zsm_setrate(ubyte prio @X, uword rate @AY) clobbers(A, X, Y)
	extsub zsmjmp + 16*3 = zsm_getrate(ubyte prio @X) clobbers() -> uword @AY
	extsub zsmjmp + 17*3 = zsm_setloop(ubyte prio @X, bool loop @Pc) clobbers(A, X, Y)
	extsub zsmjmp + 18*3 = zsm_opmatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub zsmjmp + 19*3 = zsm_psgatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub zsmjmp + 29*3 = zsm_pcmatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub zsmjmp + 21*3 = zsm_set_int_rate(ubyte value @A, ubyte frac @Y) clobbers(A, X, Y)

	extsub zsmjmp + 25*3 = zcm_setmem(ubyte slot @X, uword data_ptr @AY) clobbers(A)
	extsub zsmjmp + 26*3 = zcm_play(ubyte slot @X, ubyte volume @A) clobbers(A, X)
	extsub zsmjmp + 27*3 = zcm_stop() clobbers(A)

	extsub zsmjmp + 28*3 = zsmkit_setisr() clobbers(A)
	extsub zsmjmp + 29*3 = zsmkit_clearisr() clobbers(A)
}
