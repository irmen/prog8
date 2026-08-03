;; Auto-generated from utility_lib.sfd and utility_lib.i
;; Library base: _UtilityBase  in prog8: sys.UtilityBase
;; Bank: 9
;; Functions: 43

utility {
    %option no_symbol_prefixing
    extsub @bank 9   -30 = FindTagItem(long tagVal @D0, pointer tagList @A0) -> pointer @D0
    extsub @bank 9   -36 = GetTagData(long tagValue @D0, long defaultVal @D1, pointer tagList @A0) -> long @D0
    extsub @bank 9   -42 = PackBoolTags(long initialFlags @D0, pointer tagList @A0, pointer boolMap @A1) -> long @D0
    extsub @bank 9   -48 = NextTagItem(pointer tagListPtr @A0) -> pointer @D0
    extsub @bank 9   -54 = FilterTagChanges(pointer changeList @A0, pointer originalList @A1, long apply @D0)
    extsub @bank 9   -60 = MapTags(pointer tagList @A0, pointer mapList @A1, long mapType @D0)
    extsub @bank 9   -66 = AllocateTagItems(long numTags @D0) -> pointer @D0
    extsub @bank 9   -72 = CloneTagItems(pointer tagList @A0) -> pointer @D0
    extsub @bank 9   -78 = FreeTagItems(pointer tagList @A0)
    extsub @bank 9   -84 = RefreshTagItemClones(pointer clone @A0, pointer original @A1)
    extsub @bank 9   -90 = TagInArray(long tagValue @D0, pointer tagArray @A0) -> bool @D0
    extsub @bank 9   -96 = FilterTagItems(pointer tagList @A0, pointer filterArray @A1, long logic @D0) -> long @D0
    extsub @bank 9   -102 = CallHookPkt(pointer hook @A0, pointer object @A2, pointer paramPacket @A1) -> long @D0
    extsub @bank 9   -120 = Amiga2Date(long seconds @D0, pointer result @A0)
    extsub @bank 9   -126 = Date2Amiga(pointer date @A0) -> long @D0
    extsub @bank 9   -132 = CheckDate(pointer date @A0) -> long @D0
    extsub @bank 9   -138 = SMult32(long arg1 @D0, long arg2 @D1) -> long @D0
    extsub @bank 9   -144 = UMult32(long arg1 @D0, long arg2 @D1) -> long @D0
    extsub @bank 9   -150 = SDivMod32(long dividend @D0, long divisor @D1) -> long @D0
    extsub @bank 9   -156 = UDivMod32(long dividend @D0, long divisor @D1) -> long @D0
    extsub @bank 9   -162 = Stricmp(str string1 @A0, str string2 @A1) -> long @D0
    extsub @bank 9   -168 = Strnicmp(str string1 @A0, str string2 @A1, long length @D0) -> long @D0
    extsub @bank 9   -174 = ToUpper(ubyte character @D0) -> ubyte @D0
    extsub @bank 9   -180 = ToLower(ubyte character @D0) -> ubyte @D0
    extsub @bank 9   -186 = ApplyTagChanges(pointer list @A0, pointer changeList @A1)
    extsub @bank 9   -198 = SMult64(long arg1 @D0, long arg2 @D1) -> long @D0
    extsub @bank 9   -204 = UMult64(long arg1 @D0, long arg2 @D1) -> long @D0
    extsub @bank 9   -210 = PackStructureTags(pointer pack @A0, pointer packTable @A1, pointer tagList @A2) -> long @D0
    extsub @bank 9   -216 = UnpackStructureTags(pointer pack @A0, pointer packTable @A1, pointer tagList @A2) -> long @D0
    extsub @bank 9   -222 = AddNamedObject(pointer nameSpace @A0, pointer object @A1) -> bool @D0
    extsub @bank 9   -228 = AllocNamedObjectA(str name @A0, pointer tagList @A1) -> pointer @D0
    extsub @bank 9   -234 = AttemptRemNamedObject(pointer object @A0) -> long @D0
    extsub @bank 9   -240 = FindNamedObject(pointer nameSpace @A0, str name @A1, pointer lastObject @A2) -> pointer @D0
    extsub @bank 9   -246 = FreeNamedObject(pointer object @A0)
    extsub @bank 9   -252 = NamedObjectName(pointer object @A0) -> str @D0
    extsub @bank 9   -258 = ReleaseNamedObject(pointer object @A0)
    extsub @bank 9   -264 = RemNamedObject(pointer object @A0, pointer message @A1)
    extsub @bank 9   -270 = GetUniqueID() -> long @D0
    extsub @bank 9   -312 = VSNPrintf(str buffer @A0, long bufsize @D0, str fmt @A1, pointer data @A2) -> long @D0
    extsub @bank 9   -438 = Strncpy(str dst @A1, str src @A0, long size @D0) -> str @D0
    extsub @bank 9   -444 = Strncat(str dst @A1, str src @A0, long size @D0) -> str @D0
    extsub @bank 9   -450 = SDivMod64(long hi @D1, long lo @D0, long divisor @D2) -> long @D0
    extsub @bank 9   -456 = UDivMod64(long hi @D1, long lo @D0, long divisor @D2) -> long @D0

    ; ---- struct definitions ----

    struct ClockData {  ; total size: 14
        uword Sec  ; 0
        uword Min  ; 2
        uword Hour  ; 4
        uword Mday  ; 6
        uword Month  ; 8
        uword Year  ; 10
        uword Wday  ; 12
    }

    struct Hook {  ; total size: 20
        pointer Succ  ; 0
        pointer Pred  ; 4
        pointer Entry  ; 8
        pointer SubEntry  ; 12
        pointer Data  ; 16
    }

    struct TagItem {  ; total size: 8
        long Tag  ; 0
        long Data  ; 4
    }

    ; ---- constants ----
    const ubyte UTILITY_NAME_I = $0001
    const uword ANO_NameSpace = $0fa0
    const uword ANO_UserSpace = $0fa1
    const uword ANO_Priority = $0fa2
    const uword ANO_Flags = $0fa3
    const ubyte NSB_NODUPS = 0
    const ubyte NSF_NODUPS = $0001
    const ubyte NSB_CASE = 1
    const ubyte NSF_CASE = $0002
    const ubyte UTILITY_PACK_I = $0001
    const ubyte PSTB_SIGNED = 31
    const long PSTF_SIGNED = $80000000
    const ubyte PSTB_UNPACK = 30
    const long PSTF_UNPACK = $40000000
    const ubyte PSTB_PACK = 29
    const long PSTF_PACK = $20000000
    const ubyte PSTB_EXISTS = 26
    const long PSTF_EXISTS = $04000000
    const ubyte TAG_DONE = $0000
    const ubyte TAG_END = $0000
    const ubyte TAG_IGNORE = $0001
    const ubyte TAG_MORE = $0002
    const ubyte TAG_SKIP = $0003
    const long TAG_USER = $80000000
    const ubyte TAGFILTER_AND = $0000
    const ubyte TAGFILTER_NOT = $0001
    const ubyte MAP_REMOVE_NOT_FOUND = $0000
    const ubyte MAP_KEEP_NOT_FOUND = $0001
}
;; End of auto-generated utility_lib.sfd
