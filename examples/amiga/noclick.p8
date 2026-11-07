; NoClick enabler for Amiga trackdisk.device.
; Sets the TDPF_NOCLICK flag on all floppy units (DF0:-DF3:) so the drive
; heads step without the usual mechanical click. Requires AmigaOS 2.0+
; (trackdisk.device V36+); on earlier Kickstart versions the public unit
; structure does not contain tdu_PubFlags and this program has no effect.
;
; Compile: prog8c -target amiga1200 noclick.p8
; Usage:   run from Shell or add to Startup-Sequence/User-Startup.

%import exec
%import trackdisk
%import textio

main {
    sub start() {
        if sys.exec_version() < 36 {
            txt.print("NoClick requires Kickstart 2.0+ (V36).\n")
            return
        }
        txt.print("Enabling no-click on")
        ubyte unit = 0
        while unit < trackdisk.NUMUNITS {
            trackdisk.set_noclick(unit)
            txt.print(" DF")
            txt.print_ub(unit)
            unit++
        }
        txt.print("\n")
    }
}
