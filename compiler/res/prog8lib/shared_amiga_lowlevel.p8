;; Auto-generated from lowlevel_lib.sfd and lowlevel_lib.i
;; Library base: _LowLevelBase  in prog8: sys.LowLevelBase
;; Bank: 19
;; Functions: 15

%import exec

lowlevel {
    %option merge, no_symbol_prefixing

    sub openlib() -> bool {
        ; not opened at startup: lowlevel.library only exists on kickstart 2.0+,
        ; so it must be opened on demand and may legitimately fail
        sys.LowLevelBase = exec.OpenLibrary("lowlevel.library", 0)
        return sys.LowLevelBase!=0
    }

    sub closelib() {
        if sys.LowLevelBase!=0 {
            exec.CloseLibrary(sys.LowLevelBase)
            sys.LowLevelBase = 0
        }
    }

    extsub @bank 19   -30 = ReadJoyPort(long port @D0) -> long @D0
    extsub @bank 19   -36 = GetLanguageSelection() -> ubyte @D0
    extsub @bank 19   -48 = GetKey() -> long @D0
    extsub @bank 19   -54 = QueryKeys(^^KeyQuery queryArray @A0, long arraySize @D1)
    extsub @bank 19   -60 = AddKBInt(pointer intRoutine @A0, pointer intData @A1) -> pointer @D0
    extsub @bank 19   -66 = RemKBInt(pointer intHandle @A1)
    extsub @bank 19   -72 = SystemControlA(pointer tagList @A1) -> long @D0
    extsub @bank 19   -78 = AddTimerInt(pointer intRoutine @A0, pointer intData @A1) -> pointer @D0
    extsub @bank 19   -84 = RemTimerInt(pointer intHandle @A1)
    extsub @bank 19   -90 = StopTimerInt(pointer intHandle @A1)
    extsub @bank 19   -96 = StartTimerInt(pointer intHandle @A1, long timeInterval @D0, bool continuous @D1)
    extsub @bank 19   -102 = ElapsedTime(pointer context @A0) -> long @D0
    extsub @bank 19   -108 = AddVBlankInt(pointer intRoutine @A0, pointer intData @A1) -> pointer @D0
    extsub @bank 19   -114 = RemVBlankInt(pointer intHandle @A1)
    extsub @bank 19   -132 = SetJoyPortAttrsA(long portNumber @D0, pointer tagList @A1) -> bool @D0

    ; ---- struct definitions ----

    struct KeyQuery {  ; total size: 4
        uword KeyCode  ; 0
        uword Pressed  ; 2
    }

    ; ---- constants ----
    const ubyte LLKB_LSHIFT = 16
    const long LLKF_LSHIFT = $00010000
    const ubyte LLKB_RSHIFT = 17
    const long LLKF_RSHIFT = $00020000
    const ubyte LLKB_CAPSLOCK = 18
    const long LLKF_CAPSLOCK = $00040000
    const ubyte LLKB_CONTROL = 19
    const long LLKF_CONTROL = $00080000
    const ubyte LLKB_LALT = 20
    const long LLKF_LALT = $00100000
    const ubyte LLKB_RALT = 21
    const long LLKF_RALT = $00200000
    const ubyte LLKB_LAMIGA = 22
    const long LLKF_LAMIGA = $00400000
    const ubyte LLKB_RAMIGA = 23
    const long LLKF_RAMIGA = $00800000
    const long SJA_Dummy = $80c00100
    const ubyte SJA_TYPE_AUTOSENSE = $0000
    const ubyte SJA_TYPE_GAMECTLR = $0001
    const ubyte SJA_TYPE_MOUSE = $0002
    const ubyte SJA_TYPE_JOYSTK = $0003
    const ubyte JP_TYPE_NOTAVAIL = $0000
    const long JP_TYPE_GAMECTLR = $10000000
    const long JP_TYPE_MOUSE = $20000000
    const long JP_TYPE_JOYSTK = $30000000
    const long JP_TYPE_UNKNOWN = $40000000
    const long JP_TYPE_MASK = $f0000000
    const ubyte JPB_BUTTON_BLUE = 23
    const long JPF_BUTTON_BLUE = $00800000
    const ubyte JPB_BUTTON_RED = 22
    const long JPF_BUTTON_RED = $00400000
    const ubyte JPB_BUTTON_YELLOW = 21
    const long JPF_BUTTON_YELLOW = $00200000
    const ubyte JPB_BUTTON_GREEN = 20
    const long JPF_BUTTON_GREEN = $00100000
    const ubyte JPB_BUTTON_FORWARD = 19
    const long JPF_BUTTON_FORWARD = $00080000
    const ubyte JPB_BUTTON_REVERSE = 18
    const long JPF_BUTTON_REVERSE = $00040000
    const ubyte JPB_BUTTON_PLAY = 17
    const long JPF_BUTTON_PLAY = $00020000
    const ubyte JPB_JOY_UP = 3
    const ubyte JPF_JOY_UP = $0008
    const ubyte JPB_JOY_DOWN = 2
    const ubyte JPF_JOY_DOWN = $0004
    const ubyte JPB_JOY_LEFT = 1
    const ubyte JPF_JOY_LEFT = $0002
    const ubyte JPB_JOY_RIGHT = 0
    const ubyte JPF_JOY_RIGHT = $0001
    const ubyte JP_MHORZ_MASK = $00ff
    const uword JP_MVERT_MASK = $ff00
    const long SCON_Dummy = $80c00000
    const ubyte CDReboot_On = $0001
    const ubyte CDReboot_Off = $0000
    const ubyte CDReboot_Default = $0002
    const ubyte RAWKEY_PORT0_BUTTON_BLUE = $72
    const ubyte RAWKEY_PORT0_BUTTON_RED = $78
    const ubyte RAWKEY_PORT0_BUTTON_YELLOW = $77
    const ubyte RAWKEY_PORT0_BUTTON_GREEN = $76
    const ubyte RAWKEY_PORT0_BUTTON_FORWARD = $75
    const ubyte RAWKEY_PORT0_BUTTON_REVERSE = $74
    const ubyte RAWKEY_PORT0_BUTTON_PLAY = $73
    const ubyte RAWKEY_PORT0_JOY_UP = $79
    const ubyte RAWKEY_PORT0_JOY_DOWN = $7A
    const ubyte RAWKEY_PORT0_JOY_LEFT = $7C
    const ubyte RAWKEY_PORT0_JOY_RIGHT = $7B
    const uword RAWKEY_PORT1_BUTTON_BLUE = $172
    const uword RAWKEY_PORT1_BUTTON_RED = $178
    const uword RAWKEY_PORT1_BUTTON_YELLOW = $177
    const uword RAWKEY_PORT1_BUTTON_GREEN = $176
    const uword RAWKEY_PORT1_BUTTON_FORWARD = $175
    const uword RAWKEY_PORT1_BUTTON_REVERSE = $174
    const uword RAWKEY_PORT1_BUTTON_PLAY = $173
    const uword RAWKEY_PORT1_JOY_UP = $179
    const uword RAWKEY_PORT1_JOY_DOWN = $17A
    const uword RAWKEY_PORT1_JOY_LEFT = $17C
    const uword RAWKEY_PORT1_JOY_RIGHT = $17B
    const uword RAWKEY_PORT2_BUTTON_BLUE = $272
    const uword RAWKEY_PORT2_BUTTON_RED = $278
    const uword RAWKEY_PORT2_BUTTON_YELLOW = $277
    const uword RAWKEY_PORT2_BUTTON_GREEN = $276
    const uword RAWKEY_PORT2_BUTTON_FORWARD = $275
    const uword RAWKEY_PORT2_BUTTON_REVERSE = $274
    const uword RAWKEY_PORT2_BUTTON_PLAY = $273
    const uword RAWKEY_PORT2_JOY_UP = $279
    const uword RAWKEY_PORT2_JOY_DOWN = $27A
    const uword RAWKEY_PORT2_JOY_LEFT = $27C
    const uword RAWKEY_PORT2_JOY_RIGHT = $27B
    const uword RAWKEY_PORT3_BUTTON_BLUE = $372
    const uword RAWKEY_PORT3_BUTTON_RED = $378
    const uword RAWKEY_PORT3_BUTTON_YELLOW = $377
    const uword RAWKEY_PORT3_BUTTON_GREEN = $376
    const uword RAWKEY_PORT3_BUTTON_FORWARD = $375
    const uword RAWKEY_PORT3_BUTTON_REVERSE = $374
    const uword RAWKEY_PORT3_BUTTON_PLAY = $373
    const uword RAWKEY_PORT3_JOY_UP = $379
    const uword RAWKEY_PORT3_JOY_DOWN = $37A
    const uword RAWKEY_PORT3_JOY_LEFT = $37C
    const uword RAWKEY_PORT3_JOY_RIGHT = $37B
    const ubyte LANG_UNKNOWN = $0000
    const ubyte LANG_AMERICAN = $0001
    const ubyte LANG_ENGLISH = $0002
    const ubyte LANG_GERMAN = $0003
    const ubyte LANG_FRENCH = $0004
    const ubyte LANG_SPANISH = $0005
    const ubyte LANG_ITALIAN = $0006
    const ubyte LANG_PORTUGUESE = $0007
    const ubyte LANG_DANISH = $0008
    const ubyte LANG_DUTCH = $0009
    const ubyte LANG_NORWEGIAN = $000a
    const ubyte LANG_FINNISH = $000b
    const ubyte LANG_SWEDISH = $000c
    const ubyte LANG_JAPANESE = $000d
    const ubyte LANG_CHINESE = $000e
    const ubyte LANG_ARABIC = $000f
    const ubyte LANG_GREEK = $0010
    const ubyte LANG_HEBREW = $0011
    const ubyte LANG_KOREAN = $0012
    const long SJA_Type = $80c00101
    const long SJA_Reinitialize = $80c00102
    const long JP_BUTTON_MASK = $00fe0000
    const ubyte JP_DIRECTION_MASK = $000f
    const uword JP_MOUSE_MASK = $ffff
    const long SCON_TakeOverSys = $80c00000
    const long SCON_KillReq = $80c00001
    const long SCON_CDReboot = $80c00002
    const long SCON_StopInput = $80c00003
    const long SCON_AddCreateKeys = $80c00004
    const long SCON_RemCreateKeys = $80c00005
}
;; End of auto-generated lowlevel_lib.sfd
