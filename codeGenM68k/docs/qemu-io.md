# QEMU m68k "virt" Machine -- IO Interfaces Reference

All MMIO addresses and register layouts for the QEMU `-M virt` m68k machine.

## Memory Map

| Device | Base Address | Size | CPU IRQ |
|---|---|---|---|
| Goldfish PIC (x6) | `0xFF000000` | 6 x 0x1000 | #1-#6 |
| Goldfish RTC (x2) | `0xFF006000` | 2 x 0x1000 | #6 (PIC#6, IRQ#1) |
| Goldfish TTY | `0xFF008000` | 0x1000 | #1 (PIC#1, IRQ#32) |
| Virt Ctrl | `0xFF009000` | 0x100 | #1 (PIC#1, IRQ#1) |
| Virtio-MMIO (x128) | `0xFF010000` | 128 x 0x200 | #2-#5 (PIC#2-#5) |

### IRQ Hierarchy

```
CPU IRQ #1  -> PIC #1   (goldfish_pic.0)  -> IRQ#1 = virt-ctrl, IRQ#32 = tty
CPU IRQ #2  -> PIC #2   (goldfish_pic.1)  -> IRQ#1..#32 = virtio-mmio #1..#32
CPU IRQ #3  -> PIC #3   (goldfish_pic.2)  -> IRQ#1..#32 = virtio-mmio #33..#64
CPU IRQ #4  -> PIC #4   (goldfish_pic.3)  -> IRQ#1..#32 = virtio-mmio #65..#96
CPU IRQ #5  -> PIC #5   (goldfish_pic.4)  -> IRQ#1..#32 = virtio-mmio #97..#128
CPU IRQ #6  -> PIC #6   (goldfish_pic.5)  -> IRQ#1 = RTC, IRQ#2..#32 = unused
CPU IRQ #7  -> NMI
```

All registers are **32 bits wide**. Accesses must be 4-byte aligned.

---

## Goldfish TTY -- `0xFF008000`

Primary console. All access via 32-bit loads/stores.

### Registers

| Offset | Name | Access | Description |
|---|---|---|---|
| `0x00` | `REG_PUT_CHAR` | WO | Write one character to serial output |
| `0x04` | `REG_BYTES_READY` | RO | Number of bytes available in RX FIFO |
| `0x08` | `REG_CMD` | WO | Issue command |
| `0x10` | `REG_DATA_PTR` | WO | DMA buffer address (low 32 bits) |
| `0x14` | `REG_DATA_LEN` | WO | DMA transfer byte count |
| `0x18` | `REG_DATA_PTR_HIGH` | WO | DMA buffer address (high 32 bits) |
| `0x20` | `REG_VERSION` | RO | Returns 1 |

### Commands (write to REG_CMD)

| Value | Name | Description |
|---|---|---|
| `0` | `CMD_INT_DISABLE` | Disable interrupts |
| `1` | `CMD_INT_ENABLE` | Enable interrupts |
| `2` | `CMD_WRITE_BUFFER` | Write N bytes from guest memory to chardev |
| `3` | `CMD_READ_BUFFER` | Read N bytes from chardev into guest memory |

### Usage: putchar (blocking, non-DMA)

Write the character directly to `REG_PUT_CHAR`. QEMU sends it immediately.

```asm
putchar:
    movea.l #0xff008000, %a1
    move.l  %d0, (%a1)
    rts
```

### Usage: getchar (blocking, polled, DMA)

1. Poll `REG_BYTES_READY` until non-zero
2. Set `REG_DATA_PTR` to buffer address
3. Set `REG_DATA_LEN` to number of bytes to read (e.g. 1)
4. Write `3` (`CMD_READ_BUFFER`) to `REG_CMD`
5. Read character(s) from buffer

```asm
getchar:
    suba.l  #16, %sp
    movea.l #0xff008004, %a1     @ REG_BYTES_READY
1:  move.l  (%a1), %d0
    tst.l   %d0
    beq     1b                   @ wait until data available
    movea.l #0xff008010, %a1     @ REG_DATA_PTR
    move.l  %sp, (%a1)
    movea.l #0xff008014, %a1     @ REG_DATA_LEN
    move.l  #1, (%a1)
    movea.l #0xff008008, %a1     @ REG_CMD
    move.l  #3, (%a1)            @ CMD_READ_BUFFER
    move.b  (%sp), %d0
    adda.l  #16, %sp
    rts
```

To use interrupts instead of polling:
1. Write `1` (`CMD_INT_ENABLE`) to `REG_CMD` (offset `0x08`)
2. Enable IRQ in PIC (PIC#1 at `0xFF000000`, IRQ#32)
3. On interrupt, read `REG_BYTES_READY` then DMA data from buffer

---

## Virt Controller -- `0xFF009000`

Controls machine power state.

### Registers

| Offset | Name | Access | Description |
|---|---|---|---|
| `0x00` | `REG_FEATURES` | RO | Feature bitmask |
| `0x04` | `REG_CMD` | WO | Issue power/reset command |

### Feature Bits (REG_FEATURES)

| Bit | Name | Description |
|---|---|---|
| `0` | `FEAT_POWER_CTRL` | Power control is available |

### Commands (write to REG_CMD)

| Value | Name | Description |
|---|---|---|
| `0` | `CMD_NOOP` | No operation |
| `1` | `CMD_RESET` | Reset the VM |
| `2` | `CMD_HALT` | Shut down the VM |
| `3` | `CMD_PANIC` | Shut down with guest panic |

### Usage: halt

```asm
halt:
    movea.l #0xff009004, %a1     @ REG_CMD
    move.l  #2, (%a1)            @ CMD_HALT
    rts
```

### Usage: reset

```asm
reset:
    movea.l #0xff009004, %a1     @ REG_CMD
    move.l  #1, (%a1)            @ CMD_RESET
    rts
```

---

## Goldfish PIC -- `0xFF000000` (+ idx * 0x1000)

Programmable Interrupt Controller. Six PICs in the virt machine:

| Base Address | PIC # | CPU IRQ | Devices |
|---|---|---|---|
| `0xFF000000` | 1 | #1 | virt-ctrl (IRQ#1), tty (IRQ#32) |
| `0xFF001000` | 2 | #2 | virtio-mmio #1..#32 |
| `0xFF002000` | 3 | #3 | virtio-mmio #33..#64 |
| `0xFF003000` | 4 | #4 | virtio-mmio #65..#96 |
| `0xFF004000` | 5 | #5 | virtio-mmio #97..#128 |
| `0xFF005000` | 6 | #6 | RTC (IRQ#1) |

### Registers

| Offset | Name | Access | Description |
|---|---|---|---|
| `0x00` | `REG_STATUS` | RO | Count of pending+enabled IRQs (popcount) |
| `0x04` | `REG_IRQ_PENDING` | RO | Bitmask of pending AND enabled IRQs |
| `0x08` | `REG_IRQ_DISABLE_ALL` | WO | Disable ALL interrupts (clears pending too) |
| `0x0c` | `REG_DISABLE` | WO | Disable specific IRQs (bitmask) |
| `0x10` | `REG_ENABLE` | WO | Enable specific IRQs (bitmask) |

Interrupts are level-sensitive. Each PIC manages 32 IRQ inputs (IRQ#1 = bit 0, IRQ#32 = bit 31).

### Usage: enable IRQ in PIC

```asm
    @ Enable IRQ#32 (tty) in PIC#1 (0xFF000000)
    movea.l #0xff000010, %a1     @ REG_ENABLE of PIC#1
    move.l  #1 << 31, (%a1)      @ bit 31 = IRQ#32
```

### Usage: read pending IRQs

```asm
    movea.l #0xff000004, %a1     @ REG_IRQ_PENDING
    move.l  (%a1), %d0           @ d0 = bitmask of pending IRQs
    btst    #31, %d0             @ check if IRQ#32 (tty) is firing
```

### Usage: disable all interrupts (mask)

```asm
    movea.l #0xff000008, %a1     @ REG_IRQ_DISABLE_ALL
    move.l  #0, (%a1)            @ any value disables all
```

---

## Goldfish RTC -- `0xFF006000`

Real-time clock with a 64-bit nanosecond counter. **Big-endian** on m68k (unlike other goldfish devices which are little-endian).

### Registers

| Offset | Name | Access | Description |
|---|---|---|---|
| `0x00` | `RTC_TIME_LOW` | RW | Time value, lower 32 bits |
| `0x04` | `RTC_TIME_HIGH` | RW | Time value, upper 32 bits |
| `0x08` | `RTC_ALARM_LOW` | RW | Alarm value, lower 32 bits |
| `0x0c` | `RTC_ALARM_HIGH` | RW | Alarm value, upper 32 bits |
| `0x10` | `RTC_IRQ_ENABLED` | RW | IRQ enable (bit 0) |
| `0x14` | `RTC_CLEAR_ALARM` | WO | Clear the alarm |
| `0x18` | `RTC_ALARM_STATUS` | RO | 1 if alarm is set |
| `0x1c` | `RTC_CLEAR_INTERRUPT` | WO | Clear pending interrupt |

Reading `RTC_TIME_LOW` latches the high 32 bits; the subsequent read of `RTC_TIME_HIGH` returns that latched value. This ensures atomic 64-bit time reads.

Writing either `RTC_TIME_LOW` or `RTC_TIME_HIGH` adjusts the tick offset to set the time.

The RTC interrupt goes to PIC#6 (CPU IRQ #6), IRQ #1.

### Usage: read time

```asm
    movea.l #0xff006000, %a1     @ RTC_TIME_LOW
    move.l  (%a1), %d0           @ d0 = low 32 bits (latches high)
    movea.l #0xff006004, %a1     @ RTC_TIME_HIGH
    move.l  (%a1), %d1           @ d1 = high 32 bits
    @ now d1:d0 = 64-bit nanosecond counter
```

---

## Virtio-MMIO -- `0xFF010000` (+ idx * 0x200)

128 virtio-mmio transport devices. Each occupies 0x200 bytes.

| Start | End | Slot Range |
|---|---|---|
| `0xFF010000` | `0xFF01FFFF` | 128 slots (0x200 each) |

### Identification Registers (RO)

| Offset | Name | Value | Description |
|---|---|---|---|
| `0x000` | `MAGIC_VALUE` | `0x74726976` ("virt") |
| `0x004` | `VERSION` | 2 (modern) | 1 = legacy |
| `0x008` | `DEVICE_ID` | varies | 0 = no device |
| `0x00c` | `VENDOR_ID` | `0x554D4551` ("QEMU") |

### Key Registers (32-bit)

| Offset | Name | Access | Description |
|---|---|---|---|
| `0x010` | `DEVICE_FEATURES` | RO | Features offered by host (low 32) |
| `0x014` | `DEVICE_FEATURES_SEL` | WO | Select feature bits (high/low) |
| `0x020` | `DRIVER_FEATURES` | WO | Features accepted by driver (low 32) |
| `0x024` | `DRIVER_FEATURES_SEL` | WO | Select driver feature bits |
| `0x030` | `QUEUE_SEL` | WO | Select virtqueue |
| `0x034` | `QUEUE_NUM_MAX` | RO | Max queue size |
| `0x038` | `QUEUE_NUM` | WO | Set queue size |
| `0x044` | `QUEUE_READY` | RW | Queue ready bit |
| `0x050` | `QUEUE_NOTIFY` | WO | Kick the virtqueue |
| `0x060` | `INTERRUPT_STATUS` | RO | Interrupt reason bits |
| `0x064` | `INTERRUPT_ACK` | WO | Acknowledge interrupt |
| `0x070` | `STATUS` | RW | Device status (driver negotiation) |
| `0x080` | `QUEUE_DESC_LOW` | WO | Descriptor table address (low) |
| `0x084` | `QUEUE_DESC_HIGH` | WO | Descriptor table address (high) |
| `0x090` | `QUEUE_AVAIL_LOW` | WO | Available ring address (low) |
| `0x094` | `QUEUE_AVAIL_HIGH` | WO | Available ring address (high) |
| `0x0a0` | `QUEUE_USED_LOW` | WO | Used ring address (low) |
| `0x0a4` | `QUEUE_USED_HIGH` | WO | Used ring address (high) |
| `0x0fc` | `CONFIG_GENERATION` | RO | Config space version |
| `0x100` | `CONFIG` | RW | Device-specific config space |

### Interrupt Status Bits

| Bit | Name | Description |
|---|---|---|
| `0` | `VIRTIO_MMIO_INT_VRING` | Used buffer notification |
| `1` | `VIRTIO_MMIO_INT_CONFIG` | Config change notification |

### Usage: detect virtio device

```asm
    movea.l #0xff010000, %a1     @ first virtio slot
    move.l  0x000(%a1), %d0      @ MAGIC_VALUE
    cmp.l   #0x74726976, %d0     @ "virt" ?
    bne     no_device
    move.l  0x008(%a1), %d0      @ DEVICE_ID
    tst.l   %d0
    beq     no_device
    @ valid virtio device found, d0 = device ID
```

---

## m68k Bootinfo Protocol

Kernels can query device base addresses, memory layout, and machine properties via the m68k bootinfo protocol. QEMU places a bootinfo blob immediately after the kernel's ELF LOAD segment in guest physical memory.

### Record Format

Every bootinfo record has a 4-byte header followed by variable-length data:

```
struct bi_record {
    uint16 tag;        // tag ID (big-endian)
    uint16 size;       // total record size in bytes, including 4-byte header (big-endian)
    uint8  data[size - 4];  // tag-specific data
};
```

Records are contiguous (no padding between them). The list is terminated by a sentinel record with `tag = 0` (`BI_LAST`).

### Locating the Bootinfo Blob

QEMU loads the kernel ELF at `0x10000` (the `LOAD_ADDR`). The `_end` linker symbol marks the end of the LOAD segment. QEMU places the bootinfo at the next word-aligned address:

```c
parameters_base = (_end + 1) & ~1;
```

When writing a kernel in assembly, use:

```asm
    move.l  #_end, d0
    addq.l  #1, d0
    lsr.l   #1, d0
    lsl.l   #1, d0
    movea.l d0, a2        ; a2 = bootinfo base
```

### Parsing Loop

```asm
    movea.l d0, a2        ; a2 = bootinfo base
1$:
    clr.l   d0
    move.w  (a2)+, d0     ; tag (2 bytes)
    beq     3$            ; BI_LAST sentinel
    clr.l   d1
    move.w  (a2)+, d1     ; size = total record size (2 bytes)
    move.l  a2, a3        ; a3 -> data portion
    lea     (-4, a2, d1.l), a2  ; advance to next record
    subq.l  #4, d1        ; d1 = data size (total - header)
    ; handle tag d0 with data at a3 (d1 bytes)
    bra     1$
3$: ; done
```

**Important**: The `size` field stores the *total* record size (including the 4-byte header), not just the data size. To advance `a2` to the next record after reading the header, compute `a2 + size - 4`. To get the data payload size, compute `size - 4`.

### Tag Definitions

#### Standard Tags (Linux/m68k)

Defined in Linux `include/uapi/asm-generic/bootinfo.h`:

| Tag  | Name                | Data Size | Description                                |
|------|---------------------|-----------|--------------------------------------------|
| 0x0000 | `BI_LAST`          | 0         | End-of-list sentinel                       |
| 0x0001 | `BI_MACHTYPE`      | 4         | Machine type (enum, see below)             |
| 0x0002 | `BI_CPUTYPE`       | 4         | CPU type bitmask (see below)               |
| 0x0003 | `BI_FPUTYPE`       | 4         | FPU type bitmask                           |
| 0x0004 | `BI_MMUTYPE`       | 4         | MMU type bitmask                           |
| 0x0005 | `BI_MEMCHUNK`      | 8         | Memory chunk: `{uint32 base, uint32 size}` |
| 0x0006 | `BI_RAMDISK`       | 8         | Ramdisk: `{uint32 addr, uint32 size}`      |
| 0x0007 | `BI_COMMAND_LINE`  | variable  | Null-terminated kernel command line string |
| 0x0008 | `BI_RNG_SEED`      | 32        | Random seed for RNG initialization         |

**BI_MACHTYPE values:**

| Value | Machine    |
|-------|------------|
| 1     | Amiga      |
| 2     | Atari      |
| 3     | Mac        |
| 4     | Apollo     |
| 5     | Sun3       |
| 6     | MVME147    |
| 7     | MVME16x    |
| 8     | BVME6000   |
| 9     | HP300      |
| 10    | Q40        |
| 11    | Sun3x      |
| 12    | M54xx      |
| 13    | M5441x     |
| 14    | **VIRT**   |

**BI_CPUTYPE (bitmask):**

| Bit | CPU       |
|-----|-----------|
| 0   | 68020     |
| 1   | 68030     |
| 2   | 68040     |
| 3   | 68060     |
| 4   | ColdFire  |

#### Virt-Specific Tags (BI_VIRT_*)

Defined in `bootinfo-virt.h`:

| Tag    | Name                    | Data Size | Description                                      |
|--------|-------------------------|-----------|--------------------------------------------------|
| 0x8000 | `BI_VIRT_QEMU_VERSION` | 4         | QEMU version: `(major << 16) \| minor`           |
| 0x8001 | `BI_VIRT_GF_PIC_BASE`  | 8         | Goldfish PIC base address + IRQ count            |
| 0x8002 | `BI_VIRT_GF_RTC_BASE`  | 8         | Goldfish RTC base address + count                |
| 0x8003 | `BI_VIRT_GF_TTY_BASE`  | 8         | Goldfish TTY base address + count                |
| 0x8004 | `BI_VIRT_VIRTIO_BASE`  | 8         | Virtio-MMIO base address + mmio region size      |
| 0x8005 | `BI_VIRT_CTRL_BASE`    | 4         | Virt controller base address                     |

For tags 0x8001-0x8004, the data is two `uint32` values: `{base address, count_or_size}`.

### Example: QEMU 11.0.0 on `m68020`

Bootinfo blob (captured via QEMU monitor `xp/32x <addr>`):

```
00010490: 0x00010008 0x0000000e 0x00020008 0x00000001
000104a0: 0x0005000c 0x00000000 0x08000000 0x80000008
000104b0: 0x0b000000 0x8001000c 0xff000000 0x00000001
000104c0: 0x8002000c 0xff006000 0x000000a8 0x8003000c
000104d0: 0xff008000 0x00000027 0x8005000c 0xff009000
000104e0: 0x00000008 0x8004000c 0xff010000 0x00000028
000104f0: 0x00070008 0x00000000 0x00080028 0x0020ac0e
00010500: 0xd72bb388 0x3ec5e0e0 0x5a1534b3 0xe295bd9a
```

Decoded (from `xp/40x 0x10490` with QEMU 11.0.0, m68020, 128 MiB RAM):

| Offset | Tag                | Total | Data                                     | Meaning                           |
|--------|---------------------|-------|------------------------------------------|-----------------------------------|
| 0x10490 | BI_MACHTYPE        | 8     | 0x0000000e (14)                          | MACH_VIRT                         |
| 0x10498 | BI_CPUTYPE         | 8     | 0x00000001 (bit 0)                       | CPU_68020                         |
| 0x104a0 | BI_MEMCHUNK        | 12    | base=0x00000000, size=0x08000000         | 128 MiB RAM at 0x0                |
| 0x104ac | BI_VIRT_QEMU_VER   | 8     | 0x0B000000 (major=11, minor=0)           | QEMU 11.0                         |
| 0x104b4 | BI_VIRT_GF_PIC     | 12    | base=0xFF000000, count=1                 | 1 PIC instance                    |
| 0x104c0 | BI_VIRT_GF_RTC     | 12    | base=0xFF006000, count=168               | 168 RTC regs                      |
| 0x104cc | BI_VIRT_GF_TTY     | 12    | base=0xFF008000, count=39                | 39 TTY channels                   |
| 0x104d8 | BI_VIRT_CTRL       | 12    | base=0xFF009000, count=8                 | CTRL regs + feature bits           |
| 0x104e4 | BI_VIRT_VIRTIO     | 12    | base=0xFF010000, count=40                | 40 VIRTIO slots                   |
| 0x104f0 | BI_COMMAND_LINE    | 8     | (empty)                                  | No kernel command line            |
| 0x104f8 | BI_RNG_SEED        | 40    | 36 bytes random seed                     | RNG initialization seed           |
| 0x10520 | BI_LAST            | 4     | --                                       | End-of-list sentinel              |

Key observations:

- The `size` field is the **total** record size including the 4-byte header.
- Most virt device records use 12 bytes total (8 bytes data: `{base, count}`).
- Singletons (MACHTYPE, CPUTYPE, QEMU_VERSION) use 8 bytes total (4 bytes data).
- BI_COMMAND_LINE is always present (4 bytes of zero when empty).
- BI_RNG_SEED: total=40 (0x28), data=36 bytes — the last record before the sentinel.
- BI_LAST has `size=4` (a 4-byte all-zero header with tag=0).

### Fixed Addresses vs Bootinfo

Although the bootinfo protocol provides device addresses, the QEMU `-M virt` machine uses fixed addresses for all MMIO devices (see Memory Map above). A standalone kernel that skips bootinfo can hardcode these addresses directly.
