%import hwinfo
%import custom
%import textio

main {
    sub start() {
        bool ok = hwinfo.gather()

        txt.print("hwinfo configuration\n")
        txt.print("ready: ")
        txt.print_bool(hwinfo.ready)
        txt.spc()
        txt.print("init: ")
        txt.print_bool(ok)
        txt.nl()

        txt.print("cpu: ")
        txt.print_ub(hwinfo.cpu)
        txt.spc()
        when hwinfo.cpu {
            hwinfo.CPU::MC68000 -> txt.print("68000")
            hwinfo.CPU::MC68010 -> txt.print("68010")
            hwinfo.CPU::MC68020 -> txt.print("68020")
            hwinfo.CPU::MC68030 -> txt.print("68030")
            hwinfo.CPU::MC68040 -> txt.print("68040")
            hwinfo.CPU::MC68060 -> txt.print("68060")
            else -> txt.print("?")
        }
        txt.nl()

        txt.print("fpu?: ")
        txt.print_bool(hwinfo.fpu)
        txt.nl()

        txt.print("vbr: $")
        txt.print_ulhex(hwinfo.VBR, false)
        txt.nl()

        txt.print("videomode: ")
        txt.print_ub(hwinfo.videomode)
        txt.spc()
        when hwinfo.videomode {
            hwinfo.VIDEO::PAL -> txt.print("PAL")
            hwinfo.VIDEO::NTSC -> txt.print("NTSC")
            else -> txt.print("?")
        }
        txt.nl()

        txt.print("chipset: ")
        txt.print_ub(hwinfo.chipset)
        txt.spc()
        when hwinfo.chipset {
            custom.CHIPSET::OCS -> txt.print("OCS")
            custom.CHIPSET::ECS -> txt.print("ECS")
            custom.CHIPSET::AGA -> txt.print("AGA")
            else -> txt.print("?")
        }
        txt.nl()

        txt.print("chipRegion: base=$")
        txt.print_ulhex(hwinfo.chipRegion[0].base, false)
        txt.print(" size=")
        txt.print_l(hwinfo.chipRegion[0].size)
        txt.nl()

        txt.print("slowCount: ")
        txt.print_ub(hwinfo.slowCount)
        txt.nl()
        for i in 0 to 3 {
            if i >= hwinfo.slowCount
                break
            txt.print("  slow[")
            txt.print_ub(i)
            txt.print("] base=$")
            txt.print_ulhex(hwinfo.slowRegions[i].base as long, false)
            txt.print(" size=")
            txt.print_l(hwinfo.slowRegions[i].size)
            txt.nl()
        }

        txt.print("fastCount: ")
        txt.print_ub(hwinfo.fastCount)
        txt.nl()
        for i in 0 to 7 {
            if i >= hwinfo.fastCount
                break
            txt.print("  fast[")
            txt.print_ub(i)
            txt.print("] base=$")
            txt.print_ulhex(hwinfo.fastRegions[i].base as long, false)
            txt.print(" size=")
            txt.print_l(hwinfo.fastRegions[i].size)
            txt.nl()
        }

        txt.print("floppyCount: ")
        txt.print_ub(hwinfo.floppyCount)
        txt.nl()

        txt.print("kickVersion: ")
        txt.print_uw(hwinfo.kickVersion)
        txt.nl()
    }
}
