;; Auto-generated from asl_lib.sfd and asl_lib.i
;; Library base: _AslBase  in prog8: sys.AslBase
;; Bank: 7
;; Functions: 8

%import exec

asllib {
    %option merge, no_symbol_prefixing

    sub openlib() -> bool {
        ; not opened at startup: asl.library only exists on kickstart 2.0+,
        ; so it must be opened on demand and may legitimately fail
        sys.AslBase = exec.OpenLibrary("asl.library", 0)
        return sys.AslBase!=0
    }

    sub closelib() {
        if sys.AslBase!=0 {
            exec.CloseLibrary(sys.AslBase)
            sys.AslBase = 0
        }
    }

    extsub @bank 7   -30 = AllocFileRequest() -> ^^FileRequester @D0
    extsub @bank 7   -36 = FreeFileRequest(^^FileRequester fileReq @A0)
    extsub @bank 7   -42 = RequestFile(^^FileRequester fileReq @A0) -> bool @D0
    extsub @bank 7   -48 = AllocAslRequest(long reqType @D0, pointer tagList @A0) -> pointer @D0
    extsub @bank 7   -54 = FreeAslRequest(pointer requester @A0)
    extsub @bank 7   -60 = AslRequest(pointer requester @A0, pointer tagList @A1) -> bool @D0
    extsub @bank 7   -78 = AbortAslRequest(pointer requester @A0)
    extsub @bank 7   -84 = ActivateAslRequest(pointer requester @A0)

    ; ---- struct definitions ----

    struct FileRequester {  ; total size: 56
        ubyte[4] Reserved0  ; 0
        str File  ; 4
        str Drawer  ; 8
        ubyte[10] Reserved1  ; 12
        word LeftEdge  ; 22
        word TopEdge  ; 24
        word Width  ; 26
        word Height  ; 28
        ubyte[2] Reserved2  ; 30
        long NumArgs  ; 32
        pointer ArgList  ; 36
        pointer UserData  ; 40
        ubyte[8] Reserved3  ; 44
        str Pattern  ; 52
    }

    struct FontRequester {  ; total size: 44
        ubyte[8] Reserved0  ; 0
        str Name  ; 8
        uword YSize  ; 12
        ubyte Style  ; 14
        ubyte Flags  ; 15
        ubyte FrontPen  ; 16
        ubyte BackPen  ; 17
        ubyte DrawMode  ; 18
        ubyte SpecialDrawMode  ; 19
        pointer UserData  ; 20
        word LeftEdge  ; 24
        word TopEdge  ; 26
        word Width  ; 28
        word Height  ; 30
        ubyte[12] emb_TAttr  ; 32
    }

    struct ScreenModeRequester {  ; total size: 48
        long DisplayID  ; 0
        long DisplayWidth  ; 4
        long DisplayHeight  ; 8
        uword DisplayDepth  ; 12
        uword OverscanType  ; 14
        uword AutoScroll  ; 16
        long BitMapWidth  ; 18
        long BitMapHeight  ; 22
        word LeftEdge  ; 26
        word TopEdge  ; 28
        word Width  ; 30
        word Height  ; 32
        uword InfoOpened  ; 34
        word InfoLeftEdge  ; 36
        word InfoTopEdge  ; 38
        word InfoWidth  ; 40
        word InfoHeight  ; 42
        pointer UserData  ; 44
    }

    ; ---- constants ----
    const long ASL_TB = $80080000
    const ubyte ASL_FileRequest = $0000
    const ubyte ASL_FontRequest = $0001
    const ubyte ASL_ScreenModeRequest = $0002
    const ubyte FRB_DOWILDFUNC = 7
    const ubyte FRF_DOWILDFUNC = $0080
    const ubyte FRB_DOMSGFUNC = 6
    const ubyte FRF_DOMSGFUNC = $0040
    const ubyte FRB_DOSAVEMODE = 5
    const ubyte FRF_DOSAVEMODE = $0020
    const ubyte FRB_PRIVATEIDCMP = 4
    const ubyte FRF_PRIVATEIDCMP = $0010
    const ubyte FRB_DOMULTISELECT = 3
    const ubyte FRF_DOMULTISELECT = $0008
    const ubyte FRB_DOPATTERNS = 0
    const ubyte FRF_DOPATTERNS = $0001
    const ubyte FRB_DRAWERSONLY = 0
    const ubyte FRF_DRAWERSONLY = $0001
    const ubyte FRB_FILTERDRAWERS = 1
    const ubyte FRF_FILTERDRAWERS = $0002
    const ubyte FRB_REJECTICONS = 2
    const ubyte FRF_REJECTICONS = $0004
    const ubyte ASLFRSORTBY_Name = $0000
    const ubyte ASLFRSORTBY_Date = $0001
    const ubyte ASLFRSORTBY_Size = $0002
    const ubyte ASLFRSORTDRAWERS_First = $0000
    const ubyte ASLFRSORTDRAWERS_Mix = $0001
    const ubyte ASLFRSORTDRAWERS_Last = $0002
    const ubyte ASLFRSORTORDER_Ascend = $0000
    const ubyte ASLFRSORTORDER_Descend = $0001
    const ubyte FOB_DOFRONTPEN = 0
    const ubyte FOF_DOFRONTPEN = $0001
    const ubyte FOB_DOBACKPEN = 1
    const ubyte FOF_DOBACKPEN = $0002
    const ubyte FOB_DOFONTSTYLE = 2
    const ubyte FOF_DOFONTSTYLE = $0004
    const ubyte FOB_DODRAWMODE = 3
    const ubyte FOF_DODRAWMODE = $0008
    const ubyte FOB_FIXEDWIDTHONLY = 4
    const ubyte FOF_FIXEDWIDTHONLY = $0010
    const ubyte FOB_PRIVATE = 5
    const ubyte FOF_PRIVATE = $0020
    const ubyte FOB_DOMSGFUNC = 6
    const ubyte FOF_DOMSGFUNC = $0040
    const ubyte FOB_DOWILDFUNC = 7
    const ubyte FOF_DOWILDFUNC = $0080
    const ubyte FOB_SPECIALMODE_NONE = 0
    const ubyte FOF_SPECIALMODE_NONE = $0001
    const ubyte FOB_SPECIALMODE_OUTLINE = 1
    const ubyte FOF_SPECIALMODE_OUTLINE = $0002
    const ubyte FOB_SPECIALMODE_SHADOW = 2
    const ubyte FOF_SPECIALMODE_SHADOW = $0004
    const ubyte ASLPOS_DefaultPosition = $0000
    const ubyte ASLPOS_CenterWindow = $0001
    const ubyte ASLPOS_CenterScreen = $0002
    const ubyte ASLPOS_WindowPosition = $0003
    const ubyte ASLPOS_ScreenPosition = $0004
    const ubyte ASLPOS_CenterMouse = $0005
    const ubyte ASLPOS_MASK = $0F
    const ubyte ASLSIZE_DefaultSize = $00
    const ubyte ASLSIZE_RelativeSize = $10
    const ubyte ASLSIZE_MASK = $30
    const ubyte ASLOPTION_ASLOverrides = $40
    const long ASL_Dummy = $80080000
    const ubyte FILB_PATGAD = 0
    const ubyte FILF_PATGAD = $0001
    const ubyte FILB_MULTISELECT = 3
    const ubyte FILF_MULTISELECT = $0008
    const ubyte FILB_NEWIDCMP = 4
    const ubyte FILF_NEWIDCMP = $0010
    const ubyte FILB_SAVE = 5
    const ubyte FILF_SAVE = $0020
    const ubyte FILB_DOMSGFUNC = 6
    const ubyte FILF_DOMSGFUNC = $0040
    const ubyte FILB_DOWILDFUNC = 7
    const ubyte FILF_DOWILDFUNC = $0080
    const ubyte FIL1B_NOFILES = 0
    const ubyte FIL1F_NOFILES = $0001
    const ubyte FIL1B_MATCHDIRS = 1
    const ubyte FIL1F_MATCHDIRS = $0002
    const ubyte RFB_DOWILDFUNC = 7
    const ubyte RFF_DOWILDFUNC = $0080
    const ubyte RFB_DOMSGFUNC = 6
    const ubyte RFF_DOMSGFUNC = $0040
    const ubyte RFB_DOCOLOR = 5
    const ubyte RFF_DOCOLOR = $0020
    const ubyte RFB_NEWIDCMP = 4
    const ubyte RFF_NEWIDCMP = $0010
    const ubyte RFB_MULTISELECT = 3
    const ubyte RFF_MULTISELECT = $0008
    const ubyte RFB_PATGAD = 0
    const ubyte RFF_PATGAD = $0001
    const ubyte FONB_FRONTCOLOR = 0
    const ubyte FONF_FRONTCOLOR = $0001
    const ubyte FONB_BACKCOLOR = 1
    const ubyte FONF_BACKCOLOR = $0002
    const ubyte FONB_STYLES = 2
    const ubyte FONF_STYLES = $0004
    const ubyte FONB_DRAWMODE = 3
    const ubyte FONF_DRAWMODE = $0008
    const ubyte FONB_FIXEDWIDTH = 4
    const ubyte FONF_FIXEDWIDTH = $0010
    const ubyte FONB_NEWIDCMP = 5
    const ubyte FONF_NEWIDCMP = $0020
    const ubyte FONB_DOMSGFUNC = 6
    const ubyte FONF_DOMSGFUNC = $0040
    const ubyte FONB_DOWILDFUNC = 7
    const ubyte FONF_DOWILDFUNC = $0080
    const long ASLFR_Window = $80080002
    const long ASLFR_Screen = $80080028
    const long ASLFR_PubScreenName = $80080029
    const long ASLFR_PrivateIDCMP = $8008002a
    const long ASLFR_IntuiMsgFunc = $80080046
    const long ASLFR_SleepWindow = $8008002b
    const long ASLFR_UserData = $80080034
    const long ASLFR_PopToFront = $80080083
    const long ASLFR_Activate = $80080084
    const long ASLFR_TextAttr = $80080033
    const long ASLFR_Locale = $80080032
    const long ASLFR_TitleText = $80080001
    const long ASLFR_PositiveText = $80080012
    const long ASLFR_NegativeText = $80080013
    const long ASLFR_InitialLeftEdge = $80080003
    const long ASLFR_InitialTopEdge = $80080004
    const long ASLFR_InitialWidth = $80080005
    const long ASLFR_InitialHeight = $80080006
    const long ASLFR_InitialFile = $80080008
    const long ASLFR_InitialDrawer = $80080009
    const long ASLFR_InitialPattern = $8008000a
    const long ASLFR_InitialShowVolumes = $80080082
    const long ASLFR_Flags1 = $80080014
    const long ASLFR_Flags2 = $80080016
    const long ASLFR_DoSaveMode = $8008002c
    const long ASLFR_DoMultiSelect = $8008002d
    const long ASLFR_DoPatterns = $8008002e
    const long ASLFR_DrawersOnly = $8008002f
    const long ASLFR_FilterFunc = $80080031
    const long ASLFR_RejectIcons = $8008003c
    const long ASLFR_RejectPattern = $8008003d
    const long ASLFR_AcceptPattern = $8008003e
    const long ASLFR_FilterDrawers = $8008003f
    const long ASLFR_HookFunc = $80080007
    const long ASLFR_SetSortBy = $8008007c
    const long ASLFR_GetSortBy = $8008007d
    const long ASLFR_SetSortDrawers = $8008007e
    const long ASLFR_GetSortDrawers = $8008007f
    const long ASLFR_SetSortOrder = $80080080
    const long ASLFR_GetSortOrder = $80080081
    const long ASLFO_Window = $80080002
    const long ASLFO_Screen = $80080028
    const long ASLFO_PubScreenName = $80080029
    const long ASLFO_PrivateIDCMP = $8008002a
    const long ASLFO_IntuiMsgFunc = $80080046
    const long ASLFO_SleepWindow = $8008002b
    const long ASLFO_UserData = $80080034
    const long ASLFO_PopToFront = $80080083
    const long ASLFO_Activate = $80080084
    const long ASLFO_TextAttr = $80080033
    const long ASLFO_Locale = $80080032
    const long ASLFO_TitleText = $80080001
    const long ASLFO_PositiveText = $80080012
    const long ASLFO_NegativeText = $80080013
    const long ASLFO_InitialLeftEdge = $80080003
    const long ASLFO_InitialTopEdge = $80080004
    const long ASLFO_InitialWidth = $80080005
    const long ASLFO_InitialHeight = $80080006
    const long ASLFO_InitialName = $8008000a
    const long ASLFO_InitialSize = $8008000b
    const long ASLFO_InitialStyle = $8008000c
    const long ASLFO_InitialFlags = $8008000d
    const long ASLFO_InitialFrontPen = $8008000e
    const long ASLFO_InitialBackPen = $8008000f
    const long ASLFO_InitialDrawMode = $8008003b
    const long ASLFO_InitialSpecMode = $8008008b
    const long ASLFO_InitialCharSet = $80080086
    const long ASLFO_Flags = $80080014
    const long ASLFO_DoFrontPen = $8008002c
    const long ASLFO_DoBackPen = $8008002d
    const long ASLFO_DoStyle = $8008002e
    const long ASLFO_DoDrawMode = $8008002f
    const long ASLFO_DoSpecialMode = $8008008c
    const long ASLFO_DoCharSet = $80080087
    const long ASLFO_SampleText = $80080085
    const long ASLFO_FixedWidthOnly = $80080030
    const long ASLFO_OTagOnly = $80080088
    const long ASLFO_ScalableOnly = $80080089
    const long ASLFO_MinHeight = $80080010
    const long ASLFO_MaxHeight = $80080011
    const long ASLFO_FilterFunc = $80080031
    const long ASLFO_CSFilterFunc = $8008008a
    const long ASLFO_HookFunc = $80080007
    const long ASLFO_MaxFrontPen = $80080042
    const long ASLFO_MaxBackPen = $80080043
    const long ASLFO_ModeList = $80080015
    const long ASLFO_FrontColors = $80080040
    const long ASLFO_BackColors = $80080041
    const long ASLSM_Window = $80080002
    const long ASLSM_Screen = $80080028
    const long ASLSM_PubScreenName = $80080029
    const long ASLSM_PrivateIDCMP = $8008002a
    const long ASLSM_IntuiMsgFunc = $80080046
    const long ASLSM_SleepWindow = $8008002b
    const long ASLSM_UserData = $80080034
    const long ASLSM_PopToFront = $80080083
    const long ASLSM_Activate = $80080084
    const long ASLSM_TextAttr = $80080033
    const long ASLSM_Locale = $80080032
    const long ASLSM_TitleText = $80080001
    const long ASLSM_PositiveText = $80080012
    const long ASLSM_NegativeText = $80080013
    const long ASLSM_InitialLeftEdge = $80080003
    const long ASLSM_InitialTopEdge = $80080004
    const long ASLSM_InitialWidth = $80080005
    const long ASLSM_InitialHeight = $80080006
    const long ASLSM_InitialDisplayID = $80080064
    const long ASLSM_InitialDisplayWidth = $80080065
    const long ASLSM_InitialDisplayHeight = $80080066
    const long ASLSM_InitialDisplayDepth = $80080067
    const long ASLSM_InitialOverscanType = $80080068
    const long ASLSM_InitialAutoScroll = $80080069
    const long ASLSM_InitialInfoOpened = $8008006a
    const long ASLSM_InitialInfoLeftEdge = $8008006b
    const long ASLSM_InitialInfoTopEdge = $8008006c
    const long ASLSM_DoWidth = $8008006d
    const long ASLSM_DoHeight = $8008006e
    const long ASLSM_DoDepth = $8008006f
    const long ASLSM_DoOverscanType = $80080070
    const long ASLSM_DoAutoScroll = $80080071
    const long ASLSM_PropertyFlags = $80080072
    const long ASLSM_PropertyMask = $80080073
    const long ASLSM_MinWidth = $80080074
    const long ASLSM_MaxWidth = $80080075
    const long ASLSM_MinHeight = $80080076
    const long ASLSM_MaxHeight = $80080077
    const long ASLSM_MinDepth = $80080078
    const long ASLSM_MaxDepth = $80080079
    const long ASLSM_FilterFunc = $8008007a
    const long ASLSM_CustomSMList = $8008007b
    const long ASL_Hail = $80080001
    const long ASL_Window = $80080002
    const long ASL_LeftEdge = $80080003
    const long ASL_TopEdge = $80080004
    const long ASL_Width = $80080005
    const long ASL_Height = $80080006
    const long ASL_HookFunc = $80080007
    const long ASL_File = $80080008
    const long ASL_Dir = $80080009
    const long ASL_Pattern = $8008000a
    const long ASL_FontName = $8008000a
    const long ASL_FontHeight = $8008000b
    const long ASL_FontStyles = $8008000c
    const long ASL_FontFlags = $8008000d
    const long ASL_FrontPen = $8008000e
    const long ASL_BackPen = $8008000f
    const long ASL_MinHeight = $80080010
    const long ASL_MaxHeight = $80080011
    const long ASL_OKText = $80080012
    const long ASL_CancelText = $80080013
    const long ASL_FuncFlags = $80080014
    const long ASL_ModeList = $80080015
    const long ASL_ExtFlags1 = $80080016
}
;; End of auto-generated asl_lib.sfd
