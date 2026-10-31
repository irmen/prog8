; Amiga datatypes.library essentials example
; requires AmigaOS 3.1 (datatypes.library V40)
; Loads a file and prints its properties as discovered by datatypes.

%import datatypes
%import exec
%import intuition
%import textio
%import utility

main {
    sub start() {
        if not datatypes.openlib() {
            txt.print("requires datatypes.library\n")
            return
        }
        defer datatypes.closelib()

        str @shared filename = "?" * 80
        txt.print("name of file to open: ")
        void txt.input_chars(filename)

        pointer @shared obj = datatypes.NewDTObjectA(filename, 0)
        if obj == 0 {
            txt.print("could not create datatype object (unsupported/missing file)\n")
            return
        }
        defer datatypes.DisposeDTObject(obj)

        pointer dthptr = getDataTypeHeader(obj)
        if (dthptr as long) == 0 {
            txt.print("could not retrieve datatype descriptor\n")
            return
        }
        ^^datatypes.DataTypeHeader dth = dthptr
        printDataTypeInfo(dth)

        if dth.GroupID == datatypes.GID_SOUND {
            handleSound(obj)
        } else if dth.GroupID == datatypes.GID_PICTURE {
            handleImage(obj)
        } else {
            txt.print("no sound or picture handler for this datatype\n")
        }
    }

    sub getDataTypeHeader(pointer obj) -> pointer {
        pointer @shared datatype = 0
        long[] @shared tags = [
            datatypes.DTA_DataType, &datatype,
            utility.TAG_DONE, 0
        ]
        if datatypes.GetDTAttrsA(obj, tags) == 0 or (datatype as long) == 0
            return 0
        ^^datatypes.DataType dtn = datatype
        return dtn.dtn_Header
    }

    sub printDataTypeInfo(^^datatypes.DataTypeHeader dth) {
        if (dth.Name as long) != 0 {
            txt.print("data type: ")
            txt.print(dth.Name)
            txt.print("\n")
        }
        if (dth.BaseName as long) != 0 {
            txt.print("base name: ")
            txt.print(dth.BaseName)
            txt.print("\n")
        }
        printFourCC("group id: ", dth.GroupID)
        printFourCC("format id: ", dth.ID)
        txt.print("data format: ")
        txt.print(datatypes.GetDTString(datatypes.DTMSG_TYPE_OFFSET + (dth.Flags & datatypes.DTF_TYPE_MASK)))
        txt.print("\n")
        if dth.GroupID == datatypes.GID_PICTURE and (dth.Flags & datatypes.DTF_TYPE_MASK) == datatypes.DTF_IFF
            txt.print("IFF picture\n")
    }

    sub printFourCC(str label, long value) {
        str @shared code = "    "
        code[0] = (value >> 24) as ubyte
        code[1] = ((value >> 16) & $ff) as ubyte
        code[2] = ((value >> 8) & $ff) as ubyte
        code[3] = (value & $ff) as ubyte
        txt.print(label)
        txt.print(code)
        txt.print("\n")
    }

    sub handleSound(pointer obj) {
        printSoundProperties(obj)

        datatypes.dtTrigger[1] @shared trigger
        trigger[0].MethodID = datatypes.DTM_TRIGGER
        trigger[0].dtt_GInfo = 0
        trigger[0].dtt_Function = datatypes.STM_PLAY
        trigger[0].dtt_Data = 0

        txt.print("playing sound; press Enter to return\n")
        void datatypes.DoDTMethodA(obj, 0, 0, &trigger[0])
        ; DTM_TRIGGER is asynchronous, so keep obj alive until the user returns.
        str @shared wait = "?" * 80
        void txt.input_chars(wait)
    }

    sub printSoundProperties(pointer obj) {
        long @shared sampleLength = 0
        uword @shared period = 0
        uword @shared volume = 0
        uword @shared cycles = 0
        long[] @shared tags = [
            datatypes.SDTA_SampleLength, &sampleLength,
            datatypes.SDTA_Period, &period,
            datatypes.SDTA_Volume, &volume,
            datatypes.SDTA_Cycles, &cycles,
            utility.TAG_DONE, 0
        ]
        void datatypes.GetDTAttrsA(obj, tags)

        txt.print("exact sample rate is not exposed by OS 3.1 DataTypes\n")

        if sampleLength > 0 {
            txt.print("sample length: ")
            txt.print_l(sampleLength)
            txt.print(" samples per channel\n")
        }
        if period > 0 {
            txt.print("sample period: ")
            txt.print_uw(period)
            txt.print(" ticks\n")
        }
        if volume > 0 {
            txt.print("playback volume: ")
            txt.print_uw(volume)
            txt.print(" / 64\n")
        }
        if cycles > 0 {
            txt.print("playback cycles: ")
            txt.print_uw(cycles)
            txt.print("\n")
        }
    }

    sub handleImage(pointer obj) {
        ^^datatypes.BitMapHeader @shared bmhd = 0
        long[] @shared imageTags = [
            datatypes.PDTA_BitMapHeader, &bmhd,
            utility.TAG_DONE, 0
        ]
        if datatypes.GetDTAttrsA(obj, imageTags) == 0 or (bmhd as long) == 0 {
            txt.print("picture bitmap header unavailable\n")
            return
        }

        txt.print("width:  ")
        txt.print_uw(bmhd.Width)
        txt.print("\nheight: ")
        txt.print_uw(bmhd.Height)
        txt.print("\ndepth:  ")
        txt.print_ub(bmhd.Depth)
        txt.print("\n")

        long[] @shared objectTags = [
            intuition.GA_Left, 0,
            intuition.GA_Top, 0,
            intuition.GA_Width, 0,
            intuition.GA_Height, 0,
            intuition.ICA_TARGET, intuition.ICTARGET_IDCMP,
            utility.TAG_DONE, 0
        ]

        long[] @shared windowTags = [
            intuition.WA_Left, 20,
            intuition.WA_Top, 20,
            intuition.WA_InnerWidth, 0,
            intuition.WA_InnerHeight, 0,
            intuition.WA_Title, "DataType Viewer",
            intuition.WA_CloseGadget, true,
            intuition.WA_DragBar, true,
            intuition.WA_DepthGadget, true,
            intuition.WA_Activate, true,
            intuition.WA_IDCMP, intuition.IDCMP_CLOSEWINDOW | intuition.IDCMP_REFRESHWINDOW | intuition.IDCMP_IDCMPUPDATE,
            utility.TAG_DONE, 0
        ]
        windowTags[5] = bmhd.Width as long
        windowTags[7] = bmhd.Height as long

        ^^intuition.Window win = intuition.OpenWindowTagList(0, windowTags)
        if win == 0 {
            txt.print("could not open image window\n")
            return
        }

        objectTags[1] = win.BorderLeft as long
        objectTags[3] = win.BorderTop as long
        objectTags[5] = (win.Width - win.BorderLeft - win.BorderRight) as long
        objectTags[7] = (win.Height - win.BorderTop - win.BorderBottom) as long
        void datatypes.SetDTAttrsA(obj, 0, 0, objectTags)

        if datatypes.AddDTObject(win, 0, obj, -1) < 0 {
            txt.print("could not add image to window\n")
            intuition.CloseWindow(win)
            return
        }
        datatypes.RefreshDTObjectA(obj, win, 0, 0)

        bool running = true
        while running {
            void exec.WaitPort(win.UserPort)
            ^^intuition.IntuiMessage msg = exec.GetMsg(win.UserPort) as ^^intuition.IntuiMessage
            while msg != 0 {
                when msg.Class {
                    intuition.IDCMP_CLOSEWINDOW -> running = false
                    intuition.IDCMP_REFRESHWINDOW -> {
                        intuition.BeginRefresh(win)
                        datatypes.RefreshDTObjectA(obj, win, 0, 0)
                        intuition.EndRefresh(win, 1)
                    }
                    intuition.IDCMP_IDCMPUPDATE -> datatypes.RefreshDTObjectA(obj, win, 0, 0)
                }
                exec.ReplyMsg(msg as ^^exec.Message)
                msg = exec.GetMsg(win.UserPort) as ^^intuition.IntuiMessage
            }
        }

        void datatypes.RemoveDTObject(win, obj)
        intuition.CloseWindow(win)
    }
}
