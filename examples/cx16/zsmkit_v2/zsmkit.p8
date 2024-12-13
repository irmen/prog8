zsmkit {
	const ubyte ZSMKitBank = 1
	extsub @bank ZSMKitBank $A000 = zsm_init_engine(uword lowram @XY) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A003 = zsm_tick(ubyte type @A) clobbers(A, X, Y)
	extsub $A003 = zsm_tick_isr(ubyte type @A) clobbers(A, X, Y)

	extsub @bank ZSMKitBank $A006 = zsm_play(ubyte prio @X) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A009 = zsm_stop(ubyte prio @X) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A00C = zsm_rewind(ubyte prio @X) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A00F = zsm_close(ubyte prio @X) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A012 = zsm_getloop(ubyte prio @X) -> bool @Pc, uword @XY, ubyte @A
	extsub @bank ZSMKitBank $A015 = zsm_getptr(ubyte prio @X) -> bool @Pc, uword @XY, ubyte @A
	extsub @bank ZSMKitBank $A018 = zsm_getksptr(ubyte prio @X) clobbers(A) -> uword @XY
	extsub @bank ZSMKitBank $A01B = zsm_setbank(ubyte prio @X, ubyte bank @A)
	extsub @bank ZSMKitBank $A01E = zsm_setmem(ubyte prio @X, uword data_ptr @AY) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A021 = zsm_setatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A024 = zsm_setcb(ubyte prio @X, uword func_ptr @AY) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A027 = zsm_clearcb(ubyte prio @X) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A02A = zsm_getstate(ubyte prio @X) clobbers(X) -> bool @Pc, bool @Pz, uword @AY
	extsub @bank ZSMKitBank $A02D = zsm_setrate(ubyte prio @X, uword rate @AY) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A030 = zsm_getrate(ubyte prio @X) clobbers() -> uword @AY
	extsub @bank ZSMKitBank $A033 = zsm_setloop(ubyte prio @X, bool loop @Pc) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A036 = zsm_opmatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A039 = zsm_psgatten(ubyte prio @X, ubyte channel @Y, ubyte value @A) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A03C = zsm_pcmatten(ubyte prio @X, ubyte value @A) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A03F = zsm_set_int_rate(ubyte value @A, ubyte frac @Y) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A042 = zsm_getosptr(ubyte prio @X) clobbers(A) -> uword @XY
	extsub @bank ZSMKitBank $A045 = zsm_getpsptr(ubyte prio @X) clobbers(A) -> uword @XY
	extsub @bank ZSMKitBank $A048 = zcm_setbank(ubyte slot @X, ubyte bank @A)
	extsub @bank ZSMKitBank $A04B = zcm_setmem(ubyte slot @X, uword data_ptr @AY) clobbers(A)
	extsub @bank ZSMKitBank $A04E = zcm_play(ubyte slot @X, ubyte volume @A) clobbers(A, X)
	extsub @bank ZSMKitBank $A051 = zcm_stop() clobbers(A)

	extsub @bank ZSMKitBank $A054 = zsmkit_setisr() clobbers(A)
	extsub @bank ZSMKitBank $A057 = zsmkit_clearisr() clobbers(A)
	extsub @bank ZSMKitBank $A05A = zsmkit_version() -> ubyte @A, ubyte @X

	extsub @bank ZSMKitBank $A05D = zsm_set_ondeck_bank(ubyte prio @X, ubyte bank @A)
	extsub @bank ZSMKitBank $A060 = zsm_set_ondeck_mem(ubyte prio @X, uword data_ptr @AY) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A063 = zsm_clear_ondeck(ubyte prio @X) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A066 = zsm_midi_init(ubyte iobase @A, bool parallel @X, bool callback @Pc) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A069 = zsm_psg_suspend(ubyte channel @Y, bool suspend @Pc) clobbers(A, X, Y)
	extsub @bank ZSMKitBank $A06C = zsm_opm_suspend(ubyte channel @Y, bool suspend @Pc) clobbers(A, X, Y)
}
