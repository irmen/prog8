
; extsubs for zsmkit loaded at $0830

zsmkit {
	extsub $0830 = zsm_init_engine(ubyte bank @A) clobbers(A, X, Y)
	extsub $0833 = zsm_tick(ubyte type @A) clobbers(A, X, Y)

	extsub $0836 = zsm_play(ubyte prio @X) clobbers(A, X, Y)
	extsub $0839 = zsm_stop(ubyte prio @X) clobbers(A, X, Y)
	extsub $083c = zsm_rewind(ubyte prio @X) clobbers(A, X, Y)
	extsub $083f = zsm_close(ubyte prio @X) clobbers(A, X, Y)
	extsub $0842 = zsm_fill_buffers() clobbers(A, X, Y)
	extsub $0845 = zsm_setlfs(ubyte prio @X, ubyte lfn_sa @A, ubyte device @Y) clobbers(A, X, Y)
	extsub $0848 = zsm_setfile(ubyte prio @X, str filename @AY) clobbers(A, X, Y)
	extsub $084b = zsm_loadpcm(ubyte prio @X, uword data_ptr @AY) clobbers(X) -> uword @AY
	extsub $084e = zsm_setmem(ubyte prio @X, uword data_ptr @AY) clobbers(A, X, Y)
	extsub $0851 = zsm_setatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub $0854 = zsm_setcb(ubyte prio @X, uword func_ptr @AY) clobbers(A, X, Y)
	extsub $0857 = zsm_clearcb(ubyte prio @X) clobbers(A, X, Y)
	extsub $085A = zsm_getstate(ubyte prio @X) clobbers(X) -> bool @Pc, bool @Pz, uword @AY
	extsub $085D = zsm_setrate(ubyte prio @X, uword rate @AY) clobbers(A, X, Y)
	extsub $0860 = zsm_getrate(ubyte prio @X) clobbers() -> uword @AY
	extsub $0863 = zsm_setloop(ubyte prio @X, bool loop @Pc) clobbers(A, X, Y)
	extsub $0866 = zsm_opmatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub $0869 = zsm_psgatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub $086C = zsm_pcmatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub $086F = zsm_set_int_rate(ubyte value @A, ubyte frac @Y) clobbers(A, X, Y)

	extsub $087B = zcm_setmem(ubyte slot @X, uword data_ptr @AY) clobbers(A)
	extsub $087E = zcm_play(ubyte slot @X, ubyte volume @A) clobbers(A, X)
	extsub $0881 = zcm_stop() clobbers(A)

	extsub $0884 = zsmkit_setisr() clobbers(A)
	extsub $0887 = zsmkit_clearisr() clobbers(A)
}
