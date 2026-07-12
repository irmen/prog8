%import floats
%import textio

main {
    sub start() {
        txt.print("\nHello from Prog8 in QEMU m68k!\n")
        txt.print("raw qemu bootinfo dump:\n")
        qemu.bootinfo_dump()
        txt.print("\n\nparsed QEMU bootinfo:\n")
        bootinfo_print()
        txt.print("\nGoodbye!\n")
    }

    sub bootinfo_print() {
        long ptr = qemu.bootinfo_ptr()
        repeat {
            uword tag = peekw(ptr)
            uword size = peekw(ptr + 2)
            if tag == qemu.BI_LAST
            {
                txt.print("  BI_LAST\n")
                break
            }
            uword data_count = size - 4
            long payload = ptr + 4

            ; -- address and tag name --
            txt.print_ulhex(ptr, true)
            txt.print(": ")
            when tag {
                qemu.BI_MACHTYPE -> txt.print("BI_MACHTYPE")
                qemu.BI_CPUTYPE -> txt.print("BI_CPUTYPE")
                qemu.BI_FPUTYPE -> txt.print("BI_FPUTYPE")
                qemu.BI_MMUTYPE -> txt.print("BI_MMUTYPE")
                qemu.BI_MEMCHUNK -> txt.print("BI_MEMCHUNK")
                qemu.BI_RAMDISK -> txt.print("BI_RAMDISK")
                qemu.BI_COMMAND_LINE -> txt.print("BI_COMMAND_LINE")
                qemu.BI_RNG_SEED -> txt.print("BI_RNG_SEED")
                qemu.BI_VIRT_QEMU_VERSION -> txt.print("BI_VIRT_QEMU_VERSION")
                qemu.BI_VIRT_GF_PIC_BASE -> txt.print("BI_VIRT_GF_PIC_BASE")
                qemu.BI_VIRT_GF_RTC_BASE -> txt.print("BI_VIRT_GF_RTC_BASE")
                qemu.BI_VIRT_GF_TTY_BASE -> txt.print("BI_VIRT_GF_TTY_BASE")
                qemu.BI_VIRT_VIRTIO_BASE -> txt.print("BI_VIRT_VIRTIO_BASE")
                qemu.BI_VIRT_CTRL_BASE -> txt.print("BI_VIRT_CTRL_BASE")
                else -> {
                    txt.print("??? (tag=")
                    txt.print_uwhex(tag, true)
                    txt.print(")")
                }
            }
            txt.nl()

            ; -- decode payload --
            when tag {
                qemu.BI_MACHTYPE -> {
                    long mach = peekl(payload)
                    txt.print("  ")
                    when mach {
                        qemu.MACH_AMIGA -> txt.print("Amiga")
                        qemu.MACH_ATARI -> txt.print("Atari")
                        qemu.MACH_MAC -> txt.print("Mac")
                        qemu.MACH_APOLLO -> txt.print("Apollo")
                        qemu.MACH_SUN3 -> txt.print("Sun3")
                        qemu.MACH_MVME147 -> txt.print("MVME147")
                        qemu.MACH_MVME16x -> txt.print("MVME16x")
                        qemu.MACH_BVME6000 -> txt.print("BVME6000")
                        qemu.MACH_HP300 -> txt.print("HP300")
                        qemu.MACH_Q40 -> txt.print("Q40")
                        qemu.MACH_SUN3x -> txt.print("Sun3x")
                        qemu.MACH_M54xx -> txt.print("M54xx")
                        qemu.MACH_M5441x -> txt.print("M5441x")
                        qemu.MACH_VIRT -> txt.print("VIRT")
                        else -> txt.print("MACH_???")
                    }
                    txt.print(" (")
                    txt.print_ulhex(mach, false)
                    txt.print(")")
                }
                qemu.BI_CPUTYPE -> {
                    long cpu = peekl(payload)
                    txt.print("  CPU")
                    if cpu==0
                        txt.print("_68000")
                    else
                    {
                        if (cpu & qemu.CPU_FEATURE_68020) != 0
                            txt.print("+68020")
                        if (cpu & qemu.CPU_FEATURE_68030) != 0
                            txt.print("+68030")
                        if (cpu & qemu.CPU_FEATURE_68040) != 0
                            txt.print("+68040")
                        if (cpu & qemu.CPU_FEATURE_68060) != 0
                            txt.print("+68060")
                        if (cpu & qemu.CPU_FEATURE_COLDFIRE) != 0
                            txt.print("+ColdFire")
                    }
                    txt.print(" (")
                    txt.print_ulhex(cpu, false)
                    txt.print(")")
                }
                qemu.BI_FPUTYPE, qemu.BI_MMUTYPE -> {
                    txt.print("  ")
                    txt.print_ulhex(peekl(payload), false)
                }
                qemu.BI_MEMCHUNK -> {
                    long mem_base = peekl(payload)
                    long mem_size = peekl(payload + 4)
                    txt.print("  mem at ")
                    txt.print_ulhex(mem_base, true)
                    txt.print(" - ")
                    txt.print_ulhex(mem_base + mem_size, true)
                    txt.print("  size=")
                    txt.print_ulhex(mem_size, true)
                }
                qemu.BI_RAMDISK -> {
                    long r_addr = peekl(payload)
                    long r_size = peekl(payload + 4)
                    txt.print("  addr=")
                    txt.print_ulhex(r_addr, true)
                    txt.print(" size=")
                    txt.print_ulhex(r_size, true)
                }
                qemu.BI_COMMAND_LINE -> {
                    txt.print("  \"")
                    uword cl_idx=0
                    repeat data_count {
                        ubyte c = @(payload + cl_idx)
                        if c == 0
                            break
                        txt.chrout(c)
                        cl_idx++
                    }
                    txt.print("\"")
                }
                qemu.BI_RNG_SEED -> {
                    txt.print("  ")
                    txt.print_ub(data_count as ubyte)
                    txt.print(" bytes: ")
                    uword rng_idx=0
                    repeat data_count {
                        ubyte rng_b = @(payload + rng_idx)
                        txt.print_ubhex(rng_b, false)
                        rng_idx++
                    }
                }
                qemu.BI_VIRT_QEMU_VERSION -> {
                    long ver = peekl(payload)
                    ubyte major = (ver >> 24) as ubyte
                    ubyte minor = ((ver >> 16) & 255) as ubyte
                    txt.print("  QEMU ")
                    txt.print_ub(major)
                    txt.print(".")
                    txt.print_ub(minor)
                    txt.print(" (")
                    txt.print_ulhex(ver, false)
                    txt.print(")")
                }
                qemu.BI_VIRT_GF_PIC_BASE, qemu.BI_VIRT_GF_RTC_BASE,
                    qemu.BI_VIRT_GF_TTY_BASE, qemu.BI_VIRT_VIRTIO_BASE,
                    qemu.BI_VIRT_CTRL_BASE -> {
                    long base = peekl(payload)
                    long count = peekl(payload + 4)
                    txt.print("  base=")
                    txt.print_ulhex(base, true)
                    txt.print("  count=")
                    txt.print_ulhex(count, true)
                }
                else -> {
                    uword hex_idx=0
                    uword hex_col=0
                    repeat data_count {
                        if hex_col == 4
                        {
                            txt.print(" ")
                            hex_col = 0
                        }
                        ubyte hex_b = @(payload + hex_idx)
                        txt.print_ubhex(hex_b, false)
                        hex_idx++
                        hex_col++
                    }
                }
            }
            txt.nl()
            ptr = qemu.bootinfo_next(ptr)
        }
    }

}

