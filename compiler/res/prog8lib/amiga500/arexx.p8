;; Auto-generated from arexx_lib.sfd and arexx_lib.i
;; Library base: _RexxSysBase  in prog8: sys.RexxSysBase
;; Bank: 29
;; Functions: 17

%import exec

arexx {
    %option no_symbol_prefixing

    sub openlib() -> bool {
        sys.RexxSysBase = exec.OpenLibrary("rexxsyslib.library", 0)
        return sys.RexxSysBase!=0
    }

    sub closelib() {
        if sys.RexxSysBase!=0 {
            exec.CloseLibrary(sys.RexxSysBase)
            sys.RexxSysBase = 0
        }
    }

    extsub @bank 29   -126 = CreateArgstring(str string @A0, long length @D0) -> pointer @D0
    extsub @bank 29   -132 = DeleteArgstring(pointer argstring @A0)
    extsub @bank 29   -138 = LengthArgstring(pointer argstring @A0) -> long @D0
    extsub @bank 29   -144 = CreateRexxMsg(^^exec.MsgPort port @A0, str extension @A1, str host @D0) -> ^^RexxMsg @D0
    extsub @bank 29   -150 = DeleteRexxMsg(^^RexxMsg packet @A0)
    extsub @bank 29   -156 = ClearRexxMsg(^^RexxMsg msgptr @A0, long count @D0)
    extsub @bank 29   -162 = FillRexxMsg(^^RexxMsg msgptr @A0, long count @D0, long mask @D1) -> bool @D0
    extsub @bank 29   -168 = IsRexxMsg(^^RexxMsg msgptr @A0) -> bool @D0
    extsub @bank 29   -450 = LockRexxBase(long resource @D0)
    extsub @bank 29   -456 = UnlockRexxBase(long resource @D0)
    extsub @bank 29   -480 = CreateRexxHostPort(str basename @A0) -> ^^exec.MsgPort @D0
    extsub @bank 29   -486 = DeleteRexxHostPort(^^exec.MsgPort port @A0)
    extsub @bank 29   -492 = GetRexxVarFromMsg(str var @A0, ^^RexxMsg msgptr @A2, str value @A1) -> long @D0
    extsub @bank 29   -498 = SetRexxVarFromMsg(str var @A0, ^^RexxMsg msgptr @A2, str value @A1) -> long @D0
    extsub @bank 29   -504 = LaunchRexxScript(str script @A0, ^^exec.MsgPort replyport @A1, str extension @A2, pointer input @D1, pointer output @D2) -> ^^RexxMsg @D0
    extsub @bank 29   -510 = FreeRexxMsg(^^RexxMsg msgptr @A0)
    extsub @bank 29   -516 = GetRexxBufferFromMsg(str var @A0, ^^RexxMsg msgptr @A2, str buffer @A1, long buffer_size @D0) -> long @D0

    ; ---- struct definitions ----

    struct RexxMsg {  ; total size: 128
        ^^exec.Node Succ  ; 0
        ^^exec.Node Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ^^exec.MsgPort ReplyPort  ; 14
        uword Length  ; 18
        pointer TaskBlock  ; 20
        pointer LibBase  ; 24
        long Action  ; 28
        long Result1  ; 32
        long Result2  ; 36
        str[16] Args  ; 40
        ^^exec.MsgPort PassPort  ; 104
        str CommAddr  ; 108
        str FileExt  ; 112
        long Stdin  ; 116
        long Stdout  ; 120
        long Avail  ; 124
    }

    ; ---- constants ----
    const ubyte ERRC_MSG = $0000
    const long RC_FAIL = -1
    const ubyte RC_OK = $0000
    const ubyte RC_WARN = $0005
    const ubyte RC_ERROR = $000a
    const ubyte RC_FATAL = $0014
    const ubyte RXBUFFSZ = $00cc
    const long RXIO_EXIST = -1
    const ubyte RXIO_STRF = $0000
    const ubyte RXIO_READ = $0001
    const ubyte RXIO_WRITE = $0002
    const ubyte RXIO_APPEND = $0003
    const long RXIO_BEGIN = -1
    const ubyte RXIO_CURR = $0000
    const ubyte RXIO_END = $0001
    const ubyte DT_DEV = $0000
    const ubyte DT_DIR = $0001
    const ubyte DT_VOL = $0002
    const uword ACTION_STACK = $07d2
    const uword ACTION_QUEUE = $07d3
    const ubyte RLFB_STOP = $0006
    const ubyte RLFB_CLOSE = $0007
    const ubyte RLFMASK = $0001
    const uword RXSCHUNK = $0400
    const ubyte RXSNEST = $0020
    const ubyte RXSTPRI = $0000
    const uword RXSSTACK = $1000
    const ubyte CTB_SPACE = $0000
    const ubyte CTB_DIGIT = $0001
    const ubyte CTB_ALPHA = $0002
    const ubyte CTB_REXXSYM = $0003
    const ubyte CTB_REXXOPR = $0004
    const ubyte CTB_REXXSPC = $0005
    const ubyte CTB_UPPER = $0006
    const ubyte CTB_LOWER = $0007
    const ubyte CTF_SPACE = $0001
    const ubyte CTF_DIGIT = $0001
    const ubyte CTF_ALPHA = $0001
    const ubyte CTF_REXXSYM = $0001
    const ubyte CTF_REXXOPR = $0001
    const ubyte CTF_REXXSPC = $0001
    const ubyte CTF_UPPER = $0001
    const ubyte CTF_LOWER = $0001
    const ubyte NSB_KEEP = $0000
    const ubyte NSB_STRING = $0001
    const ubyte NSB_NOTNUM = $0002
    const ubyte NSB_NUMBER = $0003
    const ubyte NSB_BINARY = $0004
    const ubyte NSB_FLOAT = $0005
    const ubyte NSB_EXT = $0006
    const ubyte NSB_SOURCE = $0007
    const ubyte NSF_KEEP = $0001
    const ubyte NSF_STRING = $0001
    const ubyte NSF_NOTNUM = $0001
    const ubyte NSF_NUMBER = $0001
    const ubyte NSF_BINARY = $0001
    const ubyte NSF_FLOAT = $0001
    const ubyte NSF_EXT = $0001
    const ubyte NSF_SOURCE = $0001
    const ubyte MAXRMARG = $000f
    const long RXCOMM = $01000000
    const long RXFUNC = $02000000
    const long RXCLOSE = $03000000
    const long RXQUERY = $04000000
    const long RXADDFH = $07000000
    const long RXADDLIB = $08000000
    const long RXREMLIB = $09000000
    const long RXADDCON = $0A000000
    const long RXREMCON = $0B000000
    const long RXTCOPN = $0C000000
    const long RXTCCLS = $0D000000
    const ubyte RXFB_NOIO = $0010
    const ubyte RXFB_RESULT = $0011
    const ubyte RXFB_STRING = $0012
    const ubyte RXFB_TOKEN = $0013
    const ubyte RXFB_NONRET = $0014
    const ubyte RXFB_SCRIPT = $0015
    const ubyte RXFF_RESULT = $0001
    const ubyte RXFF_STRING = $0001
    const ubyte RXFF_TOKEN = $0001
    const ubyte RXFF_NONRET = $0001
    const ubyte RXFF_SCRIPT = $0001
    const long RXCODEMASK = $FF000000
    const long RXARGMASK = $0000000F
    const ubyte RRT_ANY = $0000
    const ubyte RRT_LIB = $0001
    const ubyte RRT_PORT = $0002
    const ubyte RRT_FILE = $0003
    const ubyte RRT_HOST = $0004
    const ubyte RRT_CLIP = $0005
    const ubyte GLOBALSZ = $00c8
    const ubyte NUMLISTS = $0005
    const ubyte RTFB_TRACE = $0000
    const ubyte RTFB_HALT = $0001
    const ubyte RTFB_SUSP = $0002
    const ubyte RTFB_TCUSE = $0003
    const ubyte RTFB_WAIT = $0006
    const ubyte RTFB_CLOSE = $0007
    const ubyte MEMQUANT = $0010
    const long MEMMASK = $FFFFFFF0
    const ubyte MEMQUICK = $0001
    const long MEMCLEAR = $00010000
    const ubyte ERR10_001 = $0001
    const ubyte ERR10_002 = $0002
    const ubyte ERR10_003 = $0003
    const ubyte ERR10_005 = $0005
    const ubyte ERR10_006 = $0006
    const ubyte ERR10_008 = $0008
    const ubyte ERR10_009 = $0009
    const ubyte ERR10_010 = $000a
    const ubyte ERR10_011 = $000b
    const ubyte ERR10_012 = $000c
    const ubyte ERR10_013 = $000d
    const ubyte ERR10_014 = $000e
    const ubyte ERR10_015 = $000f
    const ubyte ERR10_016 = $0010
    const ubyte ERR10_017 = $0011
    const ubyte ERR10_018 = $0012
    const ubyte ERR10_019 = $0013
    const ubyte ERR10_020 = $0014
    const ubyte ERR10_021 = $0015
    const ubyte ERR10_022 = $0016
    const ubyte ERR10_023 = $0017
    const ubyte ERR10_024 = $0018
    const ubyte ERR10_025 = $0019
    const ubyte ERR10_026 = $001a
    const ubyte ERR10_027 = $001b
    const ubyte ERR10_028 = $001c
    const ubyte ERR10_029 = $001d
    const ubyte ERR10_030 = $001e
    const ubyte ERR10_031 = $001f
    const ubyte ERR10_032 = $0020
    const ubyte ERR10_033 = $0021
    const ubyte ERR10_034 = $0022
    const ubyte ERR10_035 = $0023
    const ubyte ERR10_036 = $0024
    const ubyte ERR10_037 = $0025
    const ubyte ERR10_039 = $0027
    const ubyte ERR10_040 = $0028
    const ubyte ERR10_041 = $0029
    const ubyte ERR10_042 = $002a
    const ubyte ERR10_043 = $002b
    const ubyte ERR10_044 = $002c
    const ubyte ERR10_045 = $002d
    const ubyte ERR10_046 = $002e
    const ubyte ERR10_047 = $002f
    const ubyte ERR10_048 = $0030
    const ubyte RLFB_TRACE = $0000
    const ubyte RLFB_HALT = $0001
    const ubyte RLFB_SUSP = $0002
    const ubyte NSF_INTNUM = $0001
    const ubyte NSF_DPNUM = $0001
    const ubyte NSF_ALPHA = $0001
    const ubyte NSF_OWNED = $0001
    const ubyte KEEPSTR = $0001
    const ubyte KEEPNUM = $0001
}
;; End of auto-generated arexx_lib.sfd
