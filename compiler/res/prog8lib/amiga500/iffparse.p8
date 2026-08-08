;; Auto-generated from iffparse_lib.sfd and iffparse_lib.i
;; Library base: _IFFParseBase  in prog8: sys.IFFParseBase
;; Bank: 14
;; Functions: 40

%import exec

iffparse {
    %option no_symbol_prefixing

    sub openlib() -> bool {
        sys.IFFParseBase = exec.OpenLibrary("iffparse.library", 0)
        return sys.IFFParseBase!=0
    }

    sub closelib() {
        if sys.IFFParseBase!=0 {
            exec.CloseLibrary(sys.IFFParseBase)
            sys.IFFParseBase = 0
        }
    }

    extsub @bank 14   -30 = AllocIFF() -> pointer @D0
    extsub @bank 14   -36 = OpenIFF(pointer iff @A0, long rwMode @D0) -> long @D0
    extsub @bank 14   -42 = ParseIFF(pointer iff @A0, long control @D0) -> long @D0
    extsub @bank 14   -48 = CloseIFF(pointer iff @A0)
    extsub @bank 14   -54 = FreeIFF(pointer iff @A0)
    extsub @bank 14   -60 = ReadChunkBytes(pointer iff @A0, pointer buf @A1, long numBytes @D0) -> long @D0
    extsub @bank 14   -66 = WriteChunkBytes(pointer iff @A0, pointer buf @A1, long numBytes @D0) -> long @D0
    extsub @bank 14   -72 = ReadChunkRecords(pointer iff @A0, pointer buf @A1, long bytesPerRecord @D0, long numRecords @D1) -> long @D0
    extsub @bank 14   -78 = WriteChunkRecords(pointer iff @A0, pointer buf @A1, long bytesPerRecord @D0, long numRecords @D1) -> long @D0
    extsub @bank 14   -84 = PushChunk(pointer iff @A0, long k_type @D0, long id @D1, long size @D2) -> long @D0
    extsub @bank 14   -90 = PopChunk(pointer iff @A0) -> long @D0
    extsub @bank 14   -102 = EntryHandler(pointer iff @A0, long k_type @D0, long id @D1, long position @D2, pointer handler @A1, pointer object @A2) -> long @D0
    extsub @bank 14   -108 = ExitHandler(pointer iff @A0, long k_type @D0, long id @D1, long position @D2, pointer handler @A1, pointer object @A2) -> long @D0
    extsub @bank 14   -114 = PropChunk(pointer iff @A0, long k_type @D0, long id @D1) -> long @D0
    extsub @bank 14   -120 = PropChunks(pointer iff @A0, pointer propArray @A1, long numPairs @D0) -> long @D0
    extsub @bank 14   -126 = StopChunk(pointer iff @A0, long k_type @D0, long id @D1) -> long @D0
    extsub @bank 14   -132 = StopChunks(pointer iff @A0, pointer propArray @A1, long numPairs @D0) -> long @D0
    extsub @bank 14   -138 = CollectionChunk(pointer iff @A0, long k_type @D0, long id @D1) -> long @D0
    extsub @bank 14   -144 = CollectionChunks(pointer iff @A0, pointer propArray @A1, long numPairs @D0) -> long @D0
    extsub @bank 14   -150 = StopOnExit(pointer iff @A0, long k_type @D0, long id @D1) -> long @D0
    extsub @bank 14   -156 = FindProp(pointer iff @A0, long k_type @D0, long id @D1) -> pointer @D0
    extsub @bank 14   -162 = FindCollection(pointer iff @A0, long k_type @D0, long id @D1) -> pointer @D0
    extsub @bank 14   -168 = FindPropContext(pointer iff @A0) -> pointer @D0
    extsub @bank 14   -174 = CurrentChunk(pointer iff @A0) -> pointer @D0
    extsub @bank 14   -180 = ParentChunk(pointer contextNode @A0) -> pointer @D0
    extsub @bank 14   -186 = AllocLocalItem(long k_type @D0, long id @D1, long ident @D2, long dataSize @D3) -> pointer @D0
    extsub @bank 14   -192 = LocalItemData(pointer localItem @A0) -> pointer @D0
    extsub @bank 14   -198 = SetLocalItemPurge(pointer localItem @A0, pointer purgeHook @A1)
    extsub @bank 14   -204 = FreeLocalItem(pointer localItem @A0)
    extsub @bank 14   -210 = FindLocalItem(pointer iff @A0, long k_type @D0, long id @D1, long ident @D2) -> pointer @D0
    extsub @bank 14   -216 = StoreLocalItem(pointer iff @A0, pointer localItem @A1, long position @D0) -> long @D0
    extsub @bank 14   -222 = StoreItemInContext(pointer iff @A0, pointer localItem @A1, pointer contextNode @A2)
    extsub @bank 14   -228 = InitIFF(pointer iff @A0, long flags @D0, pointer streamHook @A1)
    extsub @bank 14   -234 = InitIFFasDOS(pointer iff @A0)
    extsub @bank 14   -240 = InitIFFasClip(pointer iff @A0)
    extsub @bank 14   -246 = OpenClipboard(long unitNumber @D0) -> pointer @D0
    extsub @bank 14   -252 = CloseClipboard(pointer clipHandle @A0)
    extsub @bank 14   -258 = GoodID(long id @D0) -> long @D0
    extsub @bank 14   -264 = GoodType(long k_type @D0) -> long @D0
    extsub @bank 14   -270 = IDtoStr(long id @D0, str buf @A0) -> str @D0

    ; ---- struct definitions ----

    struct CollectionItem {  ; total size: 12
        pointer Next  ; 0
        long Size  ; 4
        pointer Data  ; 8
    }

    struct ContextNode {  ; total size: 24
        pointer Succ  ; 0
        pointer Pred  ; 4
        long Id  ; 8
        long Type  ; 12
        long Size  ; 16
        long Scan  ; 20
    }

    struct IFFHandle {  ; total size: 12
        long Stream  ; 0
        long Flags  ; 4
        long Depth  ; 8
    }

    struct IFFStreamCmd {  ; total size: 12
        long Command  ; 0
        pointer Buf  ; 4
        long NBytes  ; 8
    }

    struct StoredProperty {  ; total size: 8
        long Size  ; 0
        pointer Data  ; 4
    }

    ; ---- constants ----
    const ubyte IFFF_READ = $0000
    const ubyte IFFF_WRITE = $0001
    const ubyte IFFF_FSEEK = $0002
    const ubyte IFFF_RSEEK = $0004
    const long IFFF_RESERVED = $FFFF0000
    const long IFFERR_EOF = -1
    const long IFFERR_EOC = -2
    const long IFFERR_NOSCOPE = -3
    const long IFFERR_NOMEM = -4
    const long IFFERR_READ = -5
    const long IFFERR_WRITE = -6
    const long IFFERR_SEEK = -7
    const long IFFERR_MANGLED = -8
    const long IFFERR_SYNTAX = -9
    const long IFFERR_NOTIFF = -10
    const long IFFERR_NOHOOK = -11
    const long IFF_RETURN2CLIENT = -12
    const long ID_FORM = $464f524d
    const long ID_LIST = $4c495354
    const long ID_CAT = $43415420
    const long ID_PROP = $50524f50
    const long ID_NULL = $20202020
    const long IFFLCI_PROP = $70726f70
    const long IFFLCI_COLLECTION = $636f6c6c
    const long IFFLCI_ENTRYHANDLER = $656e6864
    const long IFFLCI_EXITHANDLER = $65786864
    const ubyte IFFPARSE_SCAN = $0000
    const ubyte IFFPARSE_STEP = $0001
    const ubyte IFFPARSE_RAWSTEP = $0002
    const ubyte IFFSLI_ROOT = $0001
    const ubyte IFFSLI_TOP = $0002
    const ubyte IFFSLI_PROP = $0003
    const long IFFSIZE_UNKNOWN = -1
    const ubyte IFFCMD_INIT = $0000
    const ubyte IFFCMD_CLEANUP = $0001
    const ubyte IFFCMD_READ = $0002
    const ubyte IFFCMD_WRITE = $0003
    const ubyte IFFCMD_SEEK = $0004
    const ubyte IFFCMD_ENTRY = $0005
    const ubyte IFFCMD_EXIT = $0006
    const ubyte IFFCMD_PURGELCI = $0007
    const ubyte IFFF_RWBITS = $0001
    const ubyte IFFSCC_INIT = $0000
    const ubyte IFFSCC_CLEANUP = $0001
    const ubyte IFFSCC_READ = $0002
    const ubyte IFFSCC_WRITE = $0003
    const ubyte IFFSCC_SEEK = $0004
}
;; End of auto-generated iffparse_lib.sfd
