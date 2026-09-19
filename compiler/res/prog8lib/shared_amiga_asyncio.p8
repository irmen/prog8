;; Auto-generated from fd/asyncio_lib.fd and libraries/asyncio.h
;; Library base: _AsyncIOBase  in prog8: sys.AsyncIOBase
;; Bank: 100
;; Functions: 13

%import exec

asyncio {
    %option merge, no_symbol_prefixing

    sub openlib() -> bool {
        sys.AsyncIOBase = exec.OpenLibrary("asyncio.library", 0)
        return sys.AsyncIOBase!=0
    }

    sub closelib() {
        if sys.AsyncIOBase!=0 {
            exec.CloseLibrary(sys.AsyncIOBase)
            sys.AsyncIOBase = 0
        }
    }

    extsub @bank 100   -30 = OpenAsync(str fileName @A0, long mode @D0, long bufferSize @D1) -> pointer @D0
    extsub @bank 100   -36 = OpenAsyncFromFH(pointer handle @A0, long mode @D0, long bufferSize @D1) -> pointer @D0
    extsub @bank 100   -42 = CloseAsync(pointer file @A0) -> long @D0
    extsub @bank 100   -48 = SeekAsync(pointer file @A0, long position @D0, long mode @D1) -> long @D0
    extsub @bank 100   -54 = ReadAsync(pointer file @A0, pointer buffer @A1, long numBytes @D0) -> long @D0
    extsub @bank 100   -60 = WriteAsync(pointer file @A0, pointer buffer @A1, long numBytes @D0) -> long @D0
    extsub @bank 100   -66 = ReadCharAsync(pointer file @A0) -> long @D0
    extsub @bank 100   -72 = WriteCharAsync(pointer file @A0, ubyte ch @D0) -> long @D0
    extsub @bank 100   -78 = ReadLineAsync(pointer file @A0, pointer buffer @A1, long size @D0) -> long @D0
    extsub @bank 100   -84 = WriteLineAsync(pointer file @A0, str line @A1) -> long @D0
    extsub @bank 100   -90 = FGetsAsync(pointer file @A0, pointer buffer @A1, long size @D0) -> str @D0
    extsub @bank 100   -96 = FGetsLenAsync(pointer file @A0, pointer buffer @A1, long size @D0, pointer length @A2) -> str @D0
    extsub @bank 100  -102 = PeekAsync(pointer file @A0, pointer buffer @A1, long numBytes @D0) -> long @D0

    ; ---- constants ----
    const long MODE_READ = $0000
    const long MODE_WRITE = $0001
    const long MODE_APPEND = $0002
    const long MODE_START = -1
    const long MODE_CURRENT = $0000
    const long MODE_END = $0001
}
;; End of auto-generated asyncio_lib.fd
