;; Hand-maintained DataTypes subset for AmigaOS 3.1 (amiga1200, V40).
;; Provides object loading, metadata, sound playback and window attachment helpers.
;; This is NOT the full datatypes API: only DTM_TRIGGER and basic window-object
;; integration are exposed; printing, drag-selection and most method helpers are omitted.
;; Library base: _DataTypesBase  in prog8: sys.DataTypesBase
;; Bank: 31

%import arexx
%import exec
%import graphics
%import intuition

datatypes {
    %option no_symbol_prefixing

    sub openlib() -> bool {
        ; not opened at startup: datatypes.library only exists on kickstart 3.0+,
        ; so it must be opened on demand and may legitimately fail
        sys.DataTypesBase = exec.OpenLibrary("datatypes.library", 0)
        return sys.DataTypesBase!=0
    }

    sub closelib() {
        if sys.DataTypesBase!=0 {
            exec.CloseLibrary(sys.DataTypesBase)
            sys.DataTypesBase = 0
        }
    }

    ; ---- essentials: object lifecycle ----
    extsub @bank 31   -48 = NewDTObjectA(str name @D0, pointer attrs @A0) -> pointer @D0
    extsub @bank 31   -54 = DisposeDTObject(pointer o @A0)
    extsub @bank 31   -36 = ObtainDataTypeA(long k_type @D0, pointer handle @A0, pointer attrs @A1) -> pointer @D0
    extsub @bank 31   -42 = ReleaseDataType(pointer dt @A0)

    ; ---- essentials: attribute access and persistence ----
    extsub @bank 31   -60 = SetDTAttrsA(pointer o @A0, ^^intuition.Window win @A1, ^^intuition.Requester req @A2, pointer attrs @A3) -> long @D0
    extsub @bank 31   -66 = GetDTAttrsA(pointer o @A0, pointer attrs @A2) -> long @D0
    extsub @bank 31   -72 = AddDTObject(^^intuition.Window win @A0, ^^intuition.Requester req @A1, pointer o @A2, long pos @D0) -> long @D0
    extsub @bank 31   -78 = RefreshDTObjectA(pointer o @A0, ^^intuition.Window win @A1, ^^intuition.Requester req @A2, pointer attrs @A3)
    extsub @bank 31   -90 = DoDTMethodA(pointer o @A0, ^^intuition.Window win @A1, ^^intuition.Requester req @A2, pointer msg @A3) -> long @D0
    extsub @bank 31   -96 = RemoveDTObject(^^intuition.Window win @A0, pointer o @A1) -> long @D0

    ; ---- public metadata structs ----
    struct BitMapHeader {  ; total size: 20
        uword Width  ; 0
        uword Height  ; 2
        word Left  ; 4
        word Top  ; 6
        ubyte Depth  ; 8
        ubyte Masking  ; 9
        ubyte Compression  ; 10
        ubyte Pad  ; 11
        uword Transparent  ; 12
        ubyte XAspect  ; 14
        ubyte YAspect  ; 15
        word PageWidth  ; 16
        word PageHeight  ; 18
    }

    struct DataTypeHeader {  ; total size: 32
        str Name  ; 0
        str BaseName  ; 4
        str Pattern  ; 8
        pointer Mask  ; 12
        long GroupID  ; 16
        long ID  ; 20
        word MaskLen  ; 24
        word Pad  ; 26
        uword Flags  ; 28
        uword Priority  ; 30
    }

    struct DataType {  ; total size: 58
        pointer dtn_Node1_Succ  ; 0
        pointer dtn_Node1_Pred  ; 4
        ubyte dtn_Node1_Type  ; 8
        byte dtn_Node1_Pri  ; 9
        str dtn_Node1_Name  ; 10
        pointer dtn_Node2_Succ  ; 14
        pointer dtn_Node2_Pred  ; 18
        ubyte dtn_Node2_Type  ; 22
        byte dtn_Node2_Pri  ; 23
        str dtn_Node2_Name  ; 24
        ^^DataTypeHeader dtn_Header  ; 28
        pointer dtn_ToolList_Head  ; 32
        pointer dtn_ToolList_Tail  ; 36
        pointer dtn_ToolList_TailPred  ; 40
        ubyte dtn_ToolList_Type  ; 44
        ubyte dtn_ToolList_Pad  ; 45
        str dtn_FunctionName  ; 46
        pointer dtn_AttrList  ; 50
        long dtn_Length  ; 54
    }

    struct dtTrigger {  ; total size: 16
        long MethodID  ; 0
        pointer dtt_GInfo  ; 4
        long dtt_Function  ; 8
        pointer dtt_Data  ; 12
    }

    ; ---- essentials: error strings ----
    extsub @bank 31   -138 = GetDTString(long id @D0) -> str @D0

    ; ---- essentials: attribute tags and constants ----
    const long DTA_Dummy = $80001000
    const uword DTF_BINARY = $0000
    const uword DTF_ASCII = $0001
    const uword DTF_IFF = $0002
    const uword DTF_MISC = $0003
    const uword DTF_TYPE_MASK = $000f
    const uword DTF_CASE = $0010
    const uword DTF_SYSTEM1 = $1000
    const uword DTMSG_TYPE_OFFSET = 2100
    const long GID_SYSTEM = $73797374
    const long GID_TEXT = $74657874
    const long GID_DOCUMENT = $646f6375
    const long GID_SOUND = $736f756e
    const long GID_INSTRUMENT = $696e7374
    const long GID_MUSIC = $6d757369
    const long GID_PICTURE = $70696374
    const long GID_ANIMATION = $616e696d
    const long GID_MOVIE = $6d6f7669
    const long ID_8SVX = $38535658
    const long ID_ANIM = $414e494d
    const long ID_BMHD = $424d4844
    const long ID_BODY = $424f4459
    const long ID_CMAP = $434d4150
    const long ID_ILBM = $494c424d
    const long ID_VHDR = $56484452
    const long ADTA_Allocated = $800010d0
    const long ADTA_CRegs = $800010cc
    const long ADTA_ColorRegisters = $800010cb
    const long ADTA_ColorTable = $800010ce
    const long ADTA_ColorTable2 = $800010cf
    const long ADTA_Cycles = $800011fa
    const long ADTA_Depth = $8000125b
    const long ADTA_Dummy = $80001258
    const long ADTA_Frame = $8000125d
    const long ADTA_FrameIncrement = $8000125f
    const long ADTA_Frames = $8000125c
    const long ADTA_FramesPerSecond = $8000125e
    const long ADTA_GRegs = $800010cd
    const long ADTA_Height = $8000125a
    const long ADTA_KeyFrame = $800010ca
    const long ADTA_ModeID = $800010c8
    const long ADTA_NumAlloc = $800010d2
    const long ADTA_NumColors = $800010d1
    const long ADTA_Period = $800011f8
    const long ADTA_Remap = $800010d3
    const long ADTA_Sample = $800011f6
    const long ADTA_SampleLength = $800011f7
    const long ADTA_Screen = $800010d4
    const long ADTA_Volume = $800011f9
    const long ADTA_Width = $80001259
    const uword ADTM_Dummy = $00000700
    const uword ADTM_LOADFRAME = $00000701
    const uword ADTM_LOCATE = $00000706
    const uword ADTM_PAUSE = $00000704
    const uword ADTM_START = $00000703
    const uword ADTM_STOP = $00000705
    const uword ADTM_UNLOADFRAME = $00000702
    const ubyte CMP_FIBDELTA = $00000001
    const ubyte CMP_NONE = $00000000
    const long DTA_ARexxPortName = $80001194
    const long DTA_BaseName = $8000101e
    const long DTA_Busy = $8000101c
    const long DTA_Conductor = $80001023
    const long DTA_ControlPanel = $80001024
    const long DTA_Data = $80001016
    const long DTA_DataType = $80001067
    const long DTA_DestCols = $80001190
    const long DTA_DestRows = $80001191
    const long DTA_Domain = $80001068
    const long DTA_ErrorLevel = $80001020
    const long DTA_ErrorNumber = $80001021
    const long DTA_ErrorString = $80001022
    const long DTA_FrameInfo = $80001074
    const long DTA_GroupID = $8000101f
    const long DTA_Handle = $80001066
    const long DTA_Height = $8000106c
    const long DTA_HorizUnit = $80001012
    const long DTA_Immediate = $80001025
    const long DTA_LayoutProc = $8000101b
    const long DTA_Left = $80001069
    const long DTA_Methods = $80001018
    const long DTA_Name = $80001064
    const long DTA_NodeName = $80001013
    const long DTA_NominalHoriz = $8000107d
    const long DTA_NominalVert = $8000107c
    const long DTA_ObjAnnotation = $8000106f
    const long DTA_ObjAuthor = $8000106e
    const long DTA_ObjCopyright = $80001070
    const long DTA_ObjName = $8000106d
    const long DTA_ObjVersion = $80001071
    const long DTA_ObjectID = $80001072
    const long DTA_PrinterProc = $8000101a
    const long DTA_PrinterStatus = $80001019
    const long DTA_RastPort = $80001193
    const long DTA_RelBottom = $80001076
    const long DTA_RelHeight = $80001078
    const long DTA_RelRight = $80001075
    const long DTA_RelWidth = $80001077
    const long DTA_Repeat = $80001026
    const long DTA_SelectDomain = $80001079
    const long DTA_SourceType = $80001065
    const long DTA_Special = $80001192
    const long DTA_Sync = $8000101d
    const long DTA_TextAttr = $8000100a
    const long DTA_TextFont = $80001017
    const long DTA_Title = $80001014
    const long DTA_Top = $8000106a
    const long DTA_TopHoriz = $8000100f
    const long DTA_TopVert = $8000100b
    const long DTA_TotalHoriz = $80001011
    const long DTA_TotalPHoriz = $8000107b
    const long DTA_TotalPVert = $8000107a
    const long DTA_TotalVert = $8000100d
    const long DTA_TriggerMethods = $80001015
    const long DTA_UserData = $80001073
    const long DTA_VertUnit = $8000100e
    const long DTA_VisibleHoriz = $80001010
    const long DTA_VisibleVert = $8000100c
    const long DTA_Width = $8000106b
    const uword DTM_Dummy = $00000600
    const uword DTM_ABORTPRINT = $00000609
    const uword DTM_ASYNCLAYOUT = $00000603
    const uword DTM_CLEARSELECTED = $00000606
    const uword DTM_COPY = $00000607
    const uword DTM_DISPOSEMEMBER = $00000611
    const uword DTM_DRAW = $00000641
    const uword DTM_FRAMEBOX = $00000601
    const uword DTM_GOTO = $00000630
    const uword DTM_NEWMEMBER = $00000610
    const uword DTM_OBTAINDRAWINFO = $00000640
    const uword DTM_PRINT = $00000608
    const uword DTM_PROCLAYOUT = $00000602
    const uword DTM_RELEASEDRAWINFO = $00000642
    const uword DTM_REMOVEDTOBJECT = $00000604
    const uword DTM_SELECT = $00000605
    const uword DTM_TRIGGER = $00000631
    const uword DTM_WRITE = $00000650
    const ubyte DTST_CLIPBOARD = $00000003
    const ubyte DTST_FILE = $00000002
    const ubyte DTST_HOTLINK = $00000004
    const ubyte DTST_RAM = $00000001
    const ubyte DTWM_IFF = $00000000
    const ubyte DTWM_RAW = $00000001
    const long PDTA_Allocated = $800010d0
    const long PDTA_BitMap = $800010ca
    const long PDTA_BitMapHeader = $800010c9
    const long PDTA_CRegs = $800010cc
    const long PDTA_ClassBitMap = $800010d8
    const long PDTA_ColorRegisters = $800010cb
    const long PDTA_ColorTable = $800010ce
    const long PDTA_ColorTable2 = $800010cf
    const long PDTA_DestBitMap = $800010d7
    const long PDTA_FreeSourceBitMap = $800010d5
    const long PDTA_GRegs = $800010cd
    const long PDTA_Grab = $800010d6
    const long PDTA_ModeID = $800010c8
    const long PDTA_NumAlloc = $800010d2
    const long PDTA_NumColors = $800010d1
    const long PDTA_NumSparse = $800010d9
    const long PDTA_Remap = $800010d3
    const long PDTA_Screen = $800010d4
    const long PDTA_SparseTable = $800010da
    const ubyte SAMPLETYPE_Left = $00000002
    const ubyte SAMPLETYPE_Right = $00000004
    const ubyte SAMPLETYPE_Stereo = $00000006
    const long SDTA_Continuous = $800011fd
    const long SDTA_Cycles = $800011fa
    const long SDTA_Dummy = $800011f4
    const long SDTA_Period = $800011f8
    const long SDTA_Sample = $800011f6
    const long SDTA_SampleLength = $800011f7
    const long SDTA_SignalBit = $800011fc
    const long SDTA_SignalTask = $800011fb
    const long SDTA_Volume = $800011f9
    const ubyte STM_ACTIVATE_FIELD = $0000000a
    const ubyte STM_BROWSE_NEXT = $00000007
    const ubyte STM_BROWSE_PREV = $00000006
    const ubyte STM_COMMAND = $0000000b
    const ubyte STM_CONTENTS = $00000003
    const ubyte STM_FASTFORWARD = $0000000d
    const ubyte STM_INDEX = $00000004
    const ubyte STM_LOCATE = $00000010
    const ubyte STM_NEXT_FIELD = $00000008
    const ubyte STM_PAUSE = $00000001
    const ubyte STM_PLAY = $00000002
    const ubyte STM_PREV_FIELD = $00000009
    const ubyte STM_RESUME = $0000000f
    const ubyte STM_RETRACE = $00000005
    const ubyte STM_REWIND = $0000000c
    const ubyte STM_STOP = $0000000e
    const long TAG_USER = $80000000
    const long TDTA_Buffer = $8000112c
    const long TDTA_BufferLen = $8000112d
    const long TDTA_LineList = $8000112e
    const long Unity = $00010000
    const ubyte cmpByteRun1 = $00000001
    const ubyte cmpByteRun2 = $00000002
    const ubyte cmpNone = $00000000
    const ubyte mskHasMask = $00000001
    const ubyte mskHasTransparentColor = $00000002
    const ubyte mskLasso = $00000003
    const ubyte mskNone = $00000000
}
