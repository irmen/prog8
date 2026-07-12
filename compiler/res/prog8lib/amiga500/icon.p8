;; Auto-generated from icon_lib.sfd and icon_lib.i
;; Library base: _IconBase  in prog8: sys.IconBase
;; Bank: 11
;; Functions: 24

icon {
    extsub @bank 11   -54 = FreeFreeList(pointer freelist @A0)
    extsub @bank 11   -72 = AddFreeList(pointer freelist @A0, pointer mem @A1, long size @A2) -> bool @D0
    extsub @bank 11   -78 = GetDiskObject(str name @A0) -> pointer @D0
    extsub @bank 11   -84 = PutDiskObject(str name @A0, pointer diskobj @A1) -> bool @D0
    extsub @bank 11   -90 = FreeDiskObject(pointer diskobj @A0)
    extsub @bank 11   -96 = FindToolType(str toolTypeArray @A0, str typeName @A1) -> ubyte @D0
    extsub @bank 11   -102 = MatchToolValue(str typeString @A0, str value @A1) -> bool @D0
    extsub @bank 11   -108 = BumpRevision(str newname @A0, str oldname @A1) -> str @D0
    extsub @bank 11   -114 = FreeAlloc(pointer free @A0, long len @A1, long k_type @A2) -> pointer @D0
    extsub @bank 11   -120 = GetDefDiskObject(long k_type @D0) -> pointer @D0
    extsub @bank 11   -126 = PutDefDiskObject(pointer diskObject @A0) -> bool @D0
    extsub @bank 11   -132 = GetDiskObjectNew(str name @A0) -> pointer @D0
    extsub @bank 11   -138 = DeleteDiskObject(str name @A0) -> bool @D0
    extsub @bank 11   -144 = FreeFree(pointer fl @A0, pointer address @A1)
    extsub @bank 11   -150 = DupDiskObjectA(pointer diskObject @A0, pointer tags @A1) -> pointer @D0
    extsub @bank 11   -156 = IconControlA(pointer icon @A0, pointer tags @A1) -> long @D0
    extsub @bank 11   -162 = DrawIconStateA(pointer rp @A0, pointer icon @A1, str label @A2, long leftOffset @D0, long topOffset @D1, long state @D2, pointer tags @A3)
    extsub @bank 11   -168 = GetIconRectangleA(pointer rp @A0, pointer icon @A1, str label @A2, pointer rect @A3, pointer tags @A4) -> bool @D0
    extsub @bank 11   -174 = NewDiskObject(long k_type @D0) -> pointer @D0
    extsub @bank 11   -180 = GetIconTagList(str name @A0, pointer tags @A1) -> pointer @D0
    extsub @bank 11   -186 = PutIconTagList(str name @A0, pointer icon @A1, pointer tags @A2) -> bool @D0
    extsub @bank 11   -192 = LayoutIconA(pointer icon @A0, pointer screen @A1, pointer tags @A2) -> bool @D0
    extsub @bank 11   -198 = ChangeToSelectedIconColor(pointer cr @A0)
    extsub @bank 11   -204 = BumpRevisionLength(str newname @A0, str oldname @A1, long maxLength @D0) -> str @D0
}

;; End of auto-generated icon_lib.sfd
