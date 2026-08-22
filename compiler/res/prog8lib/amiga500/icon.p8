;; Auto-generated from icon_lib.sfd and icon_lib.i
;; Library base: _IconBase  in prog8: sys.IconBase
;; Bank: 11
;; Functions: 24

%import graphics
%import intuition

icon {
    %option no_symbol_prefixing
    extsub @bank 11   -54 = FreeFreeList(pointer freelist @A0)
    extsub @bank 11   -72 = AddFreeList(pointer freelist @A0, pointer mem @A1, long size @A2) -> bool @D0
    extsub @bank 11   -78 = GetDiskObject(str name @A0) -> ^^DiskObject @D0
    extsub @bank 11   -84 = PutDiskObject(str name @A0, ^^DiskObject diskobj @A1) -> bool @D0
    extsub @bank 11   -90 = FreeDiskObject(^^DiskObject diskobj @A0)
    extsub @bank 11   -96 = FindToolType(str toolTypeArray @A0, str typeName @A1) -> pointer @D0
    extsub @bank 11   -102 = MatchToolValue(str typeString @A0, str value @A1) -> bool @D0
    extsub @bank 11   -108 = BumpRevision(str newname @A0, str oldname @A1) -> str @D0
    extsub @bank 11   -114 = FreeAlloc(pointer free @A0, long len @A1, long k_type @A2) -> pointer @D0
    extsub @bank 11   -120 = GetDefDiskObject(long k_type @D0) -> ^^DiskObject @D0
    extsub @bank 11   -126 = PutDefDiskObject(^^DiskObject diskObject @A0) -> bool @D0
    extsub @bank 11   -132 = GetDiskObjectNew(str name @A0) -> ^^DiskObject @D0
    extsub @bank 11   -138 = DeleteDiskObject(str name @A0) -> bool @D0
    extsub @bank 11   -144 = FreeFree(pointer fl @A0, pointer address @A1)
    extsub @bank 11   -150 = DupDiskObjectA(^^DiskObject diskObject @A0, pointer tags @A1) -> ^^DiskObject @D0
    extsub @bank 11   -156 = IconControlA(^^DiskObject icon @A0, pointer tags @A1) -> long @D0
    extsub @bank 11   -162 = DrawIconStateA(^^graphics.RastPort rp @A0, ^^DiskObject icon @A1, str label @A2, long leftOffset @D0, long topOffset @D1, long state @D2, pointer tags @A3)
    extsub @bank 11   -168 = GetIconRectangleA(^^graphics.RastPort rp @A0, ^^DiskObject icon @A1, str label @A2, pointer rect @A3, pointer tags @A4) -> bool @D0
    extsub @bank 11   -174 = NewDiskObject(long k_type @D0) -> ^^DiskObject @D0
    extsub @bank 11   -180 = GetIconTagList(str name @A0, pointer tags @A1) -> ^^DiskObject @D0
    extsub @bank 11   -186 = PutIconTagList(str name @A0, ^^DiskObject icon @A1, pointer tags @A2) -> bool @D0
    extsub @bank 11   -192 = LayoutIconA(^^DiskObject icon @A0, ^^intuition.Screen screen @A1, pointer tags @A2) -> bool @D0
    extsub @bank 11   -198 = ChangeToSelectedIconColor(pointer cr @A0)
    extsub @bank 11   -204 = BumpRevisionLength(str newname @A0, str oldname @A1, long maxLength @D0) -> str @D0

    ; ---- struct definitions ----

    struct DiskObject {  ; total size: 38
        uword Magic  ; 0
        uword Version  ; 2
        ubyte[4] emb_do_Gadget  ; 4
        ubyte Type  ; 8
        ubyte Pad_byte  ; 9
        pointer DefaultTool  ; 10
        pointer ToolTypes  ; 14
        long CurrentX  ; 18
        long CurrentY  ; 22
        pointer DrawerData  ; 26
        pointer ToolWindow  ; 30
        long StackSize  ; 34
    }

    ; ---- constants ----
    const ubyte WORKBENCH_WORKBENCH_I = $0001
    const ubyte WBDISK = $0001
    const ubyte WBDRAWER = $0002
    const ubyte WBTOOL = $0003
    const ubyte WBPROJECT = $0004
    const ubyte WBGARBAGE = $0005
    const ubyte WBDEVICE = $0006
    const ubyte WBKICK = $0007
    const ubyte WBAPPICON = $0008
    const ubyte DDVM_BYDEFAULT = $0000
    const ubyte DDVM_BYICON = $0001
    const ubyte DDVM_BYNAME = $0002
    const ubyte DDVM_BYDATE = $0003
    const ubyte DDVM_BYSIZE = $0004
    const ubyte DDVM_BYTYPE = $0005
    const uword DDFLAGS_SHOWMASK = $0003
    const uword DDFLAGS_SHOWDEFAULT = $0000
    const uword DDFLAGS_SHOWICONS = $0001
    const uword DDFLAGS_SHOWALL = $0002
    const uword DDFLAGS_SORTMASK = $0300
    const uword DDFLAGS_SORTDEFAULT = $0000
    const uword DDFLAGS_SORTASC = $0100
    const uword DDFLAGS_SORTDESC = $0200
    const uword WB_DISKMAGIC = $e310
    const ubyte WB_DISKVERSION = $0001
    const ubyte WB_DISKREVISION = $0001
    const ubyte WB_DISKREVISIONMASK = $ff
    const uword GFLG_GADGBACKFILL = $0001
    const uword GADGBACKFILL = $0001
    const long NO_ICON_POSITION = $80000000
    const ubyte AM_VERSION = $0001
    const ubyte AMTYPE_APPWINDOW = $0007
    const ubyte AMTYPE_APPICON = $0008
    const ubyte AMTYPE_APPMENUITEM = $0009
    const ubyte AMTYPE_APPWINDOWZONE = $000a
    const ubyte AMCLASSICON_Open = $0000
    const ubyte AMCLASSICON_Copy = $0001
    const ubyte AMCLASSICON_Rename = $0002
    const ubyte AMCLASSICON_Information = $0003
    const ubyte AMCLASSICON_Snapshot = $0004
    const ubyte AMCLASSICON_UnSnapshot = $0005
    const ubyte AMCLASSICON_LeaveOut = $0006
    const ubyte AMCLASSICON_PutAway = $0007
    const ubyte AMCLASSICON_Delete = $0008
    const ubyte AMCLASSICON_FormatDisk = $0009
    const ubyte AMCLASSICON_EjectDisk = $000d
    const ubyte AMCLASSICON_EmptyTrash = $000a
    const ubyte AMCLASSICON_Selected = $000b
    const ubyte AMCLASSICON_Unselected = $000c
    const long WBA_Dummy = $8000a000
    const ubyte SCHMSTATE_TryCleanup = $0000
    const ubyte SCHMSTATE_Cleanup = $0001
    const ubyte SCHMSTATE_Setup = $0002
    const long WBF_DRAWERPOSMASK = $00000003
    const long WBF_DRAWERPOSFREE = $00000000
    const long WBF_DRAWERPOSHEAD = $00000001
    const long WBF_DRAWERPOSTAIL = $00000002
    const long WBF_BOUNDTEXTVIEW = $00000080
    const long WBF_OLDDATESFIRST = $00010000
    const ubyte WBO_NONE = $0000
    const ubyte WBO_DRAWER = $0001
    const ubyte WBO_ICON = $0002
    const ubyte ADZMACTION_Enter = $0000
    const ubyte ADZMACTION_Leave = $0001
    const ubyte ISMACTION_Unselect = $0000
    const ubyte ISMACTION_Select = $0001
    const ubyte ISMACTION_Ignore = $0002
    const ubyte ISMACTION_Stop = $0003
    const ubyte CPACTION_Begin = $0000
    const ubyte CPACTION_Copy = $0001
    const ubyte CPACTION_End = $0002
    const ubyte DLACTION_BeginDiscard = $0000
    const ubyte DLACTION_BeginEmptyTrash = $0001
    const ubyte DLACTION_DeleteContents = $0003
    const ubyte DLACTION_DeleteObject = $0004
    const ubyte DLACTION_End = $0005
    const ubyte TIACTION_Rename = $0000
    const ubyte TIACTION_RelabelVolume = $0001
    const ubyte TIACTION_NewDrawer = $0002
    const ubyte TIACTION_Execute = $0003
    const ubyte UPDATEWB_ObjectRemoved = $0000
    const ubyte UPDATEWB_ObjectAdded = $0001
    const long WBAPPICONA_SupportsOpen = $8000a001
    const long WBAPPICONA_SupportsCopy = $8000a002
    const long WBAPPICONA_SupportsRename = $8000a003
    const long WBAPPICONA_SupportsInformation = $8000a004
    const long WBAPPICONA_SupportsSnapshot = $8000a005
    const long WBAPPICONA_SupportsUnSnapshot = $8000a006
    const long WBAPPICONA_SupportsLeaveOut = $8000a007
    const long WBAPPICONA_SupportsPutAway = $8000a008
    const long WBAPPICONA_SupportsDelete = $8000a009
    const long WBAPPICONA_SupportsFormatDisk = $8000a00a
    const long WBAPPICONA_SupportsEjectDisk = $8000a09e
    const long WBAPPICONA_SupportsEmptyTrash = $8000a00b
    const long WBAPPICONA_PropagatePosition = $8000a00c
    const long WBAPPICONA_RenderHook = $8000a00d
    const long WBAPPICONA_NotifySelectState = $8000a00e
    const long WBAPPMENUA_CommandKeyString = $8000a00f
    const long WBAPPMENUA_GetKey = $8000a041
    const long WBAPPMENUA_UseKey = $8000a042
    const long WBAPPMENUA_GetTitleKey = $8000a04d
    const long WBOPENA_ArgLock = $8000a010
    const long WBOPENA_ArgName = $8000a011
    const long WBOPENA_Show = $8000a04b
    const long WBOPENA_ViewBy = $8000a04c
    const long WBCTRLA_IsOpen = $8000a012
    const long WBCTRLA_DuplicateSearchPath = $8000a013
    const long WBCTRLA_FreeSearchPath = $8000a014
    const long WBCTRLA_GetDefaultStackSize = $8000a015
    const long WBCTRLA_SetDefaultStackSize = $8000a016
    const long WBCTRLA_RedrawAppIcon = $8000a017
    const long WBCTRLA_GetProgramList = $8000a018
    const long WBCTRLA_FreeProgramList = $8000a019
    const long WBCTRLA_GetSelectedIconList = $8000a024
    const long WBCTRLA_FreeSelectedIconList = $8000a025
    const long WBCTRLA_GetOpenDrawerList = $8000a026
    const long WBCTRLA_FreeOpenDrawerList = $8000a027
    const long WBCTRLA_GetHiddenDeviceList = $8000a02a
    const long WBCTRLA_FreeHiddenDeviceList = $8000a02b
    const long WBCTRLA_AddHiddenDeviceName = $8000a02c
    const long WBCTRLA_RemoveHiddenDeviceName = $8000a02d
    const long WBCTRLA_GetTypeRestartTime = $8000a02f
    const long WBCTRLA_SetTypeRestartTime = $8000a030
    const long WBCTRLA_GetCopyHook = $8000a045
    const long WBCTRLA_SetCopyHook = $8000a046
    const long WBCTRLA_GetDeleteHook = $8000a047
    const long WBCTRLA_SetDeleteHook = $8000a048
    const long WBCTRLA_GetTextInputHook = $8000a049
    const long WBCTRLA_SetTextInputHook = $8000a04a
    const long WBCTRLA_AddSetupCleanupHook = $8000a04e
    const long WBCTRLA_RemSetupCleanupHook = $8000a04f
    const long WBCTRLA_SetGlobalFlags = $8000a082
    const long WBCTRLA_GetGlobalFlags = $8000a083
    const long WBCTRLA_GetDiskInfoHook = $8000a09f
    const long WBCTRLA_SetDiskInfoHook = $8000a0a0
    const long WBDZA_Left = $8000a01a
    const long WBDZA_RelRight = $8000a01b
    const long WBDZA_Top = $8000a01c
    const long WBDZA_RelBottom = $8000a01d
    const long WBDZA_Width = $8000a01e
    const long WBDZA_RelWidth = $8000a01f
    const long WBDZA_Height = $8000a020
    const long WBDZA_RelHeight = $8000a021
    const long WBDZA_Box = $8000a022
    const long WBDZA_Hook = $8000a023
    const long WBOBJA_Type = $8000a056
    const long WBOBJA_Left = $8000a057
    const long WBOBJA_Top = $8000a058
    const long WBOBJA_Width = $8000a059
    const long WBOBJA_Height = $8000a05a
    const long WBOBJA_State = $8000a05b
    const long WBOBJA_IsFake = $8000a05c
    const long WBOBJA_Name = $8000a05d
    const long WBOBJA_NameSize = $8000a05e
    const long WBOBJA_FullPath = $8000a05f
    const long WBOBJA_FullPathSize = $8000a060
    const long WBOBJA_IsLink = $8000a061
    const long WBOBJA_DrawerPath = $8000a062
    const long WBOBJA_DrawerPathSize = $8000a063
    const long WBOBJA_DrawerFlags = $8000a074
    const long WBOBJA_DrawerModes = $8000a075
}
;; End of auto-generated icon_lib.sfd
