; serial routines for the serial/wifi card of the X16.
; assumes the wifi part is supported via zimodem.

%import strings

serial {

    %option ignore_unused

    const ubyte REG_TXRX_BUFFER         = 0
    const ubyte REG_INTERRUPT_ENABLE    = 1
    const ubyte REG_INTERRUPT_IDENT     = 2
    const ubyte REG_FIFO_CONTROL        = 2
    const ubyte REG_LINE_CONTROL        = 3
    const ubyte REG_MODEM_CONTROL       = 4
    const ubyte REG_LINE_STATUS         = 5
    const ubyte REG_MODEM_STATUS        = 6
    const ubyte REG_SCRATCH	            = 7
    const ubyte REG_DIVISOR_LATCH_LOW	= 0	;same as TXRX, but when bit-7 of line control high
    const ubyte REG_DIVISOR_LATCH_HI	= 1	;same as IRQE, but when bit-7 of line control high

    ; Baud rate divisor values for TL16C2550 with 14.7456 MHz clock
    ; Formula: divisor = 14745600 / (16 * baudrate)
    enum BAUD {
        B921600 = $0001,
        B460800 = $0002,
        B230400 = $0004,
        B115200 = $0008,
        B57600  = $0010,
        B38400  = $0018,
        B28800  = $0020,
        B19200  = $0030,
        B14400  = $0040,
        B9600   = $0060,
        B4800   = $00C0,
        B2400   = $0180,
        B1200   = $0300,
        B600    = $0600,
        B300    = $0C00,
    }

    ; Bitfield masks for TL16C2550's Line Status Register
    enum LSR {
        DR   = %00000001,   ; Data Ready
        OE   = %00000010,   ; Overrun Error
        PE   = %00000100,   ; Parity Error
        FE   = %00001000,   ; Framing Error
        BI   = %00010000,   ; Break Interrupt
        THRE = %00100000,   ; Transmit Holding Register Empty
        TEMT = %01000000,   ; Transmitter Empty
        RCVR = %10000000,   ; Error in RCVR FIFO
    }

    ; generic UART routines (not ZiModem specific):

    sub detect_uarts() -> uword, uword {
        ; Detects the presence of UART chips (up to 2) on the system.
        ; Returns the discovered UART chip addresses, or 0 if none found.

        const uword FIRST_UART_ADDRESS = $9F60
        const uword LAST_UART_ADDRESS = $9FF8

        alias uart1 = cx16.r1
        alias uart2 = cx16.r2
        uart1 = uart2 = 0

        for cx16.r0 in FIRST_UART_ADDRESS to LAST_UART_ADDRESS step 8 {
            if probe(cx16.r0) {
                if uart1 == 0
                    uart1 = cx16.r0
                else if uart2 == 0 {
                    uart2 = cx16.r0
                    break
                }
            }
        }
        return uart1, uart2

        private sub probe(uword address) -> bool {
            sys.set_irqd()
            address[REG_INTERRUPT_ENABLE] = $f0
            bool found = address[REG_INTERRUPT_ENABLE] & $f0 == 0       ; The high nybble of IER is always clear
            address[REG_INTERRUPT_ENABLE] = 0
            if found {
                address[REG_MODEM_CONTROL] = $ff
                found = address[REG_MODEM_CONTROL] == $3f           ; Bits 7 and 6 of MCR are always clear
                address[REG_MODEM_CONTROL] = 0
            }
            sys.clear_irqd()
            return found
        }
    }

    sub initialize_uart(uword uart_addr) {
        ; 921600 baud, 8,N,1, AutoFlow Control, FIFOS, no interrupts
        uart_addr[REG_LINE_CONTROL] = $80          ; Set DLAB
        uart_addr[REG_DIVISOR_LATCH_HI] = msb(BAUD::B921600)
        uart_addr[REG_DIVISOR_LATCH_LOW] = lsb(BAUD::B921600)
        uart_addr[REG_LINE_CONTROL] = $03          ; 8,N,1, DLAB off
        uart_addr[REG_FIFO_CONTROL] = $C3          ; FIFO enable & reset, trigger 14
        uart_addr[REG_MODEM_CONTROL] = $23         ; DTR/RTS + AutoFlow Control
        uart_addr[REG_INTERRUPT_ENABLE] = $00      ; No interrupts
    }

    sub write(uword @zp uart_addr, str data) {
        ; Writes the string data to the uart.
        alias data_ptr = cx16.r0
        data_ptr = data
        while @(data_ptr) != 0 {
            while uart_addr[REG_LINE_STATUS] & LSR::THRE == 0  {
                ; wait until data can be written
            }
            @(uart_addr) = @(data_ptr)
            data_ptr++
        }
    }

    sub read_until(uword @zp uart_addr, str match, ^^ubyte buffer, uword max_size) -> uword {
        ; Reads bytes from the uart into buffer until the match string is found.
        ; The match string itself is also included in the buffer.
        ; Stops when the match string is found or max_size bytes have been read.
        ; Returns the number of bytes read.

        alias buffer_ptr = cx16.r0
        alias match_ptr = cx16.r1
        alias data = cx16.r2L
        alias read_size = cx16.r3
        alias max_read_size = cx16.r4

        match_ptr = match
        buffer_ptr = buffer
        max_read_size = max_size
        read_size = 0

        while read_size < max_read_size {
            while uart_addr[REG_LINE_STATUS] & LSR::DR == 0  {
                ; wait until data is present
            }
            @(buffer_ptr) = data = @(uart_addr)
            buffer_ptr++
            read_size++
            if data == @(match_ptr) {
                match_ptr++
                if @(match_ptr)==0
                    break
            }
            else
                match_ptr = match
        }

        return read_size
    }

    sub discard_until(uword @zp uart_addr, str match) {
        ; Reads bytes from the uart until the match string is found, discards all data.
        ; The match string itself is also discarded.

        alias match_ptr = cx16.r0
        match_ptr = match

        repeat {
            while uart_addr[REG_LINE_STATUS] & LSR::DR == 0  {
                ; wait until data is present
            }
            if @(uart_addr) == @(match_ptr) {
                match_ptr++
                if @(match_ptr)==0
                    break
            }
            else
                match_ptr = match
        }
    }

    ; zimodem routines:

    sub zi_initialize(uword uart_addr) {
        ; initializes the ZiModem on the given uart address
        zi_uart = uart_addr
        if zi_uart == 0
            return      ; no uart present

        initialize_uart(zi_uart)
        ; ZiModem sends a version banner of the `ati` command when the ESP32 boots up.
        ; Read it off if present. It is not always ready immediately so wait a tiny bit
        sys.wait(5)
        write(zi_uart, "\x03")      ; first send CTRL-C (+ no echo) to abort any previous file streams
        sys.wait(10)
        zi_write_cmd(iso:"ate0q0v1x1f0r1s45=3&p0&k3")
        discard_until(zi_uart, iso:"OK\x0d\x0a")
    }

    sub zi_reset() {
        ; reset all zimodem connections
        write(zi_uart, "\x03")      ; first send CTRL-C (+ no echo) to abort any previous file streams
        sys.wait(10)
        serial.zi_write_cmd(iso:"atz")
        discard_until(zi_uart, iso:"OK\x0d\x0a")
    }

    sub zi_write_cmd(str command) {
        ; writes the zimodem command (followed by a proper line ending)
        write(zi_uart, command)
        write_eol(zi_uart)
    }

    sub zi_start_get_file_hexmode(str filename) -> bool {
        ; starts a file download from the given url. Returns true if ok, false if file could not be found
        ; you can download chunks of the file by repeatedly calling zi_get_file_chunk_hexmode() until that returns 0.
        ; Note: this uses zimodem hex mode transfer for now because binary mode transfer is broken at the moment.
        zi_write_cmd(iso:"ats45=1")  ; enable hex mode transfer (unfortunately this also forces caching mode on the esp32)
        discard_until(zi_uart, iso:"OK\x0d\x0a")
        write(zi_uart, iso:"at&g\"")
        write(zi_uart, filename)
        write(zi_uart, "\"\x0d\x0a")

        if read_one(zi_uart)=='[' {
            ; note: zimodem file size in hex mode is bogus (only can go up to $FFFF), just skip the whole header line.
            discard_until(zi_uart, "\x0d\x0a")
            return true
        } else {
            discard_until(zi_uart, iso:"RROR\x0d\x0a")
            return false
        }
    }

    sub zi_start_get_file(str filename) -> long {
        ; TODO: binary file transfer seems unreliable at the moment (text files are still ok, but often freezes on binary files. Could be a problem with my particular X16 serial card or zimodem firmware though), use hex mode transfer for reliable results.
        ; starts a file download from the given url, returns the size of the file to download.
        ; you can download chunks of the file by repeatedly calling zi_get_file_chunk() until that returns 0
        write(zi_uart, iso:"at&g\"")
        write(zi_uart, filename)
        write(zi_uart, "\"\x0d\x0a")

        if read_one(zi_uart)=='[' {
            ;  parse header  [ 0 <filesize> <sum> ]
            ubyte[25] header_buf
            void read_until(zi_uart, iso:"]\x0d\x0a", &header_buf, sizeof(header_buf))
            return conv.str2long(&header_buf + 3)
        } else {
            discard_until(zi_uart, iso:"RROR\x0d\x0a")
            return 0
        }
    }

    sub zi_get_file_chunk_hexmode(^^ubyte @zp buffer, uword buffer_size) -> uword {
        ; read the next chunk of data from the file (opened in hex mode), into buffer, up to buffer_size bytes (minimum 40 bytes!!)
        ; returns the number of bytes read.  0 if no more data was available.
        ubyte[90] linebuffer
        cx16.r0 = read_until(zi_uart, "\x0d\x0a", linebuffer, sizeof(linebuffer))
        if cx16.r0 == 4 and linebuffer[0] == iso:'O'        ; OK\r\n  was received -- end of transmission
            return 0

        ubyte digits = lsb(cx16.r0)-2
        decode(digits)
        return digits/2

        private sub decode(ubyte numdigits) {
            cx16.r1L = 0
            for cx16.r0L in 0 to numdigits-1 step 2 {
                buffer[cx16.r1L] = decodehex(cx16.r0L)
                cx16.r1L++
            }

            asmsub decodehex(ubyte digitindex @Y) -> ubyte @A {
                %asm {{
                    lda  p8v_linebuffer,y
                    sec
                    sbc #$30
                    cmp #$0a
                    bmi +
                    sbc #$07
            +       asl a
                    asl a
                    asl a
                    asl a
                    sta P8ZP_SCRATCH_REG
                    lda  p8v_linebuffer+1,y
                    sec
                    sbc #$30
                    cmp #$0a
                    bmi +
                    sbc #$07
            +       ora P8ZP_SCRATCH_REG
                    rts
                }}
            }
        }
    }

    sub zi_get_file_chunk(^^ubyte @zp buffer, uword buffer_size, long remaining_file_size) -> uword {
        ; TODO: binary file transfer seems unreliable at the moment (text files are still ok, but often freezes on binary files. Could be a problem with my particular X16 serial card or zimodem firmware though), use hex mode transfer for reliable results.
        ; read the next chunk of data from the file, into buffer, up to buffer_size bytes.
        ; returns the number of bytes read.  0 if no more data was available.

        uword bytes_to_read = if msw(remaining_file_size)!=0 then buffer_size else min(lsw(remaining_file_size), buffer_size)

        repeat bytes_to_read {
            while zi_uart[REG_LINE_STATUS] & LSR::DR == 0 {
                ; wait until data is present
            }
            @(buffer) = @(zi_uart)
            buffer++
        }

        return bytes_to_read
    }

    sub zi_end_get_file() {
        ; makes sure the 'OK' response after the actual file data is also read away
        discard_until(zi_uart, iso:"OK\x0d\x0a")
    }

    sub zi_get_ip_address() -> str {
        ; returns the ip address of the zimodem wifi connection. Assumes IPV4 only.
        ubyte[25] ip_buffer
        zi_write_cmd(iso:"ati2")
        void read_until(zi_uart, iso:"OK\x0d\x0a", &ip_buffer, len(ip_buffer))
        cx16.r0 = &ip_buffer
        while not strings.isspace(@(cx16.r0)) {
            cx16.r0++
        }
        @(cx16.r0) = 0
        return &ip_buffer
    }


    ; private stuff

    private uword @zp zi_uart          ; the uart address to use for ZiModem

    private sub read_one(uword @zp uart_addr) -> ubyte {
        while uart_addr[REG_LINE_STATUS] & LSR::DR == 0  {
            ; wait until data is present
        }
        return @(uart_addr)
    }

    private sub write_eol(uword uart_addr) {
        write(uart_addr, "\x0d\x0a")
    }
}
