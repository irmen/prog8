%import syslib

audio {
    const ubyte rom_bank = $0a
    asmsub audio_init() clobbers(A,X,Y) -> bool @Pc { ; (re)initialize both vera PSG and YM audio chips
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.audio_init
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_fmfreq(ubyte channel @A, uword freq @XY, bool noretrigger @Pc) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_fmfreq
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_fmnote(ubyte channel @A, ubyte note @X, ubyte fracsemitone @Y, bool noretrigger @Pc) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_fmnote
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_fmplaystring(ubyte length @A, str string @XY) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_fmplaystring
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_fmvib(ubyte speed @A, ubyte depth @X) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_fmvib
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_playstringvoice(ubyte channel @A) clobbers(Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_playstringvoice
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_psgfreq(ubyte voice @A, uword freq @XY) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_psgfreq
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_psgnote(ubyte voice @A, ubyte note @X, ubyte fracsemitone @Y) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_psgnote
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_psgwav(ubyte voice @A, ubyte waveform @X) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_psgwav
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_psgplaystring(ubyte length @A, str string @XY) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_psgplaystring
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_fmchordstring(ubyte length @A, str string @XY) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_fmchordstring
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub bas_psgchordstring(ubyte length @A, str string @XY) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.bas_psgchordstring
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_bas2fm(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_bas2fm
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_bas2midi(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_bas2midi
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_bas2psg(ubyte note @X, ubyte fracsemitone @Y) clobbers(A) -> uword @XY, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_bas2psg
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_fm2bas(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_fm2bas
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_fm2midi(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_fm2midi
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_fm2psg(ubyte note @X, ubyte fracsemitone @Y) clobbers(A) -> uword @XY, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_fm2psg
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_freq2bas(uword freqHz @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_freq2bas
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_freq2fm(uword freqHz @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_freq2fm
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_freq2midi(uword freqHz @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_freq2midi
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_freq2psg(uword freqHz @XY) clobbers(A) -> uword @XY, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_freq2psg
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_midi2bas(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_midi2bas
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_midi2fm(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_midi2fm
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_midi2psg(ubyte note @X, ubyte fracsemitone @Y) clobbers(A) -> uword @XY, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_midi2psg
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_psg2bas(uword freq @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_psg2bas
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_psg2fm(uword freq @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_psg2fm
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub notecon_psg2midi(uword freq @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.notecon_psg2midi
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_init() clobbers(A,X,Y) {              ; (re)init Vera PSG
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_init
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_playfreq(ubyte voice @A, uword freq @XY) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_playfreq
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_read(ubyte offset @X, bool cookedVol @Pc) clobbers(Y) -> ubyte @A{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_read
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_setatten(ubyte voice @A, ubyte attenuation @X) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_setatten
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_setfreq(ubyte voice @A, uword freq @XY) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_setfreq
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_setpan(ubyte voice @A, ubyte panning @X) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_setpan
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_setvol(ubyte voice @A, ubyte volume @X) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_setvol
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_write(ubyte value @A, ubyte offset @X) clobbers(Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_write
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_write_fast(ubyte value @A, ubyte offset @X) clobbers(Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_write_fast
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_getatten(ubyte voice @A) clobbers(Y) -> ubyte @X{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_getatten
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub psg_getpan(ubyte voice @A) clobbers(Y) -> ubyte @X{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.psg_getpan
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_init() clobbers(A,X,Y) -> bool @Pc {            ; (re)init YM chip
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_init
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_loaddefpatches() clobbers(A,X,Y) -> bool @Pc {   ; load default YM patches
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_loaddefpatches
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_loadpatch(ubyte channel @A, uword patchOrAddress @XY, bool what @Pc) clobbers(A,X,Y){
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_loadpatch
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_loadpatchlfn(ubyte channel @A, ubyte lfn @X) clobbers(X,Y) -> ubyte @A, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_loadpatchlfn
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_playdrum(ubyte channel @A, ubyte note @X) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_playdrum
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_playnote(ubyte channel @A, ubyte kc @X, ubyte kf @Y, bool notrigger @Pc) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_playnote
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_setatten(ubyte channel @A, ubyte attenuation @X) clobbers(Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_setatten
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_setdrum(ubyte channel @A, ubyte note @X) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_setdrum
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_setnote(ubyte channel @A, ubyte kc @X, ubyte kf @Y) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_setnote
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_setpan(ubyte channel @A, ubyte panning @X) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_setpan
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_read(ubyte register @X, bool cooked @Pc) clobbers(Y) -> ubyte @A, bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_read
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_release(ubyte channel @A) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_release
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_trigger(ubyte channel @A, bool noRelease @Pc) clobbers(A,X,Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_trigger
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_write(ubyte value @A, ubyte register @X) clobbers(Y) -> bool @Pc{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_write
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_getatten(ubyte channel @A) clobbers(Y) -> ubyte @X{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_getatten
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_getpan(ubyte channel @A) clobbers(Y) -> ubyte @X{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_getpan
            .byte p8c_rom_bank
            rts
        }}
    }
    asmsub ym_get_chip_type() clobbers(X) -> ubyte @A{
        %asm{{
            jsr cx16.JSRFAR
            .word cx16.ym_get_chip_type
            .byte p8c_rom_bank
            rts
        }}
    }
}
