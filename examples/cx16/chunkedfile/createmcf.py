import struct
from typing import Sequence

# Chunk types:
# user types: 0 - 239
# reserved: 240 - 249
CHUNK_DUMMY = 250
CHUNK_SYSTEMRAM = 251
CHUNK_VIDEORAM = 252
CHUNK_PAUSE = 253
CHUNK_EOF = 254
CHUNK_IGNORE = 255


class LoadList:
    def __init__(self):
        self.data = bytearray([CHUNK_IGNORE] * 256)
        self.data[0:4] = struct.pack("ccBB", b'L', b'L', 1, 0)
        self.index = 4

    def __eq__(self, other) -> bool:
        return isinstance(other, LoadList) and self.data == other.data

    def add_chunk(self, chunktype: int, size: int, bank: int = 0, address: int = 0) -> None:
        if chunktype < 0 or chunktype > 255:
            raise ValueError("chunktype must be 0 - 255")
        if size < 0 or size > 65535:
            raise ValueError(f"size must be 0 - 65535 bytes")
        if bank < 0 or bank > 31:
            raise ValueError("bank must be 0 - 31")
        if address < 0 or address > 65535:
            raise ValueError("address must be 0 - 65535")
        data = struct.pack("<BHBH", chunktype, size, bank, address)
        if self.index + len(data) > 255:
            raise IndexError("too many chunks")
        self.data[self.index: self.index + len(data)] = data
        self.index += len(data)

    def read(self, data: bytes) -> None:
        if len(data) != 256:
            raise ValueError("data must be 256 bytes long")
        self.data[0:256] = data
        self.index = 256

    def parse(self) -> Sequence[tuple]:
        if self.data[0] != ord('L') or self.data[1] != ord('L') or self.data[2] != 1:
            raise ValueError("invalid loadlist identifier", self.data[:4])
        index = 4
        chunks = []
        while index < 256:
            chunktype = self.data[index]
            if chunktype == CHUNK_IGNORE:
                index += 1  # pad byte
            elif chunktype == CHUNK_EOF:
                chunks.append((CHUNK_EOF, 0, 0, 0))
                return chunks
            else:
                size, bank, address = struct.unpack("<HBH", self.data[index + 1:index + 6])
                chunks.append((chunktype, size, bank, address))
                index += 6
        return chunks

    def is_empty(self) -> bool:
        return self.index <= 4


class MultiChunkFile:
    def __init__(self):
        self.chunks = []
        self.loadlist = LoadList()
        self.datachunks = []

    def read(self, filename: str) -> None:
        num_loadlist = 0
        num_data = 0
        data_total = 0
        file_total = 0
        with open(filename, "rb") as inf:
            while True:
                data = inf.read(256)
                if len(data) == 0:
                    break
                loadlist = LoadList()
                loadlist.read(data)
                self.chunks.append(loadlist)
                num_loadlist += 1
                file_total += len(data)
                for chunk in loadlist.parse():
                    if chunk[0] == CHUNK_EOF:
                        break
                    elif chunk[0] in (CHUNK_DUMMY, CHUNK_SYSTEMRAM, CHUNK_VIDEORAM) or chunk[0] < 240:
                        data = inf.read(chunk[1])
                        self.chunks.append(data)
                        num_data += 1
                        data_total += len(data)
                        file_total += len(data)
        self.validate()
        print("Read", filename)
        print(f"  {num_loadlist} loadlists, {num_data} data chunks")
        print(f"  total data size: {data_total} (${data_total:x})")
        print(f"  total file size: {file_total} (${file_total:x})")

    def write(self, filename: str) -> None:
        self.flush_ll()
        self.validate()
        num_loadlist = 0
        num_data = 0
        data_total = 0
        file_total = 0
        with open(filename, "wb") as out:
            for chunk in self.chunks:
                if isinstance(chunk, LoadList):
                    num_loadlist += 1
                    out.write(chunk.data)
                    file_total += len(chunk.data)
                elif isinstance(chunk, bytes):
                    num_data += 1
                    out.write(chunk)
                    file_total += len(chunk)
                    data_total += len(chunk)
                else:
                    raise TypeError("invalid chunk type")
        print("Written", filename)
        print(f"  {num_loadlist} loadlists, {num_data} data chunks")
        print(f"  total data size: {data_total} (${data_total:x})")
        print(f"  total file size: {file_total} (${file_total:x})")

    def validate(self) -> None:
        if len(self.chunks) < 2:
            raise ValueError("must be at least 2 chunks", self.chunks)
        chunk_iter = iter(self.chunks)
        eof_found = False
        while not eof_found:
            try:
                chunk = next(chunk_iter)
            except StopIteration:
                raise ValueError("missing EOF chunk")
            else:
                if isinstance(chunk, LoadList):
                    for lc in chunk.parse():
                        if lc[0] == CHUNK_EOF:
                            eof_found = True
                            break
                        elif lc[0] in (CHUNK_DUMMY, CHUNK_SYSTEMRAM, CHUNK_VIDEORAM) or lc[0] < 240:
                            size, bank, address = lc[1:]
                            data = next(chunk_iter)
                            if isinstance(data, bytes):
                                if len(data) != size:
                                    raise ValueError("data chunk size mismatch")
                            else:
                                raise TypeError("expected data chunk")
                else:
                    raise TypeError("expected LoadList chunk")
        try:
            next(chunk_iter)
        except StopIteration:
            pass
        else:
            raise ValueError("trailing chunks")

    def add_Dummy(self, size: int) -> None:
        self.add_chunk(CHUNK_DUMMY, data=bytearray(size))

    def add_SystemRam(self, bank: int, address: int, data: bytes, chunksize: int=0xfe00) -> None:
        if address >= 0xa000 and address < 0xc000:
            raise ValueError("use add_BankedRam instead to load chunks into banked ram $a000-$c000")
        while data:
            if address >= 65536:
                raise ValueError("data too large for system ram")
            self.add_chunk(CHUNK_SYSTEMRAM, bank, address, data[:chunksize])
            data = data[chunksize:]
            address += chunksize

    def add_BankedRam(self, bank: int, address: int, data: bytes, chunksize: int=0x2000) -> None:
        if address < 0xa000 or address >= 0xc000:
            raise ValueError("use add_SystemRam instead to load chunks into normal system ram")
        if chunksize>0x2000:
            raise ValueError("chunksize too large for banked ram")
        while data:
            if address >= 0xc000:
                address -= 0xc000
                bank += 1
                if bank >= 32:
                    raise ValueError("data too large for banked ram")
            self.add_chunk(CHUNK_SYSTEMRAM, bank, address, data[:chunksize])
            data = data[chunksize:]
            address += chunksize

    def add_VideoRam(self, bank: int, address: int, data: bytes, chunksize: int=0xfe00) -> None:
        if bank < 0 or bank > 1:
            raise ValueError("bank for videoram must be 0 or 1")
        while data:
            if address >= 65536:
                address -= 65536
                bank += 1
                if bank >= 2:
                    raise ValueError("data too large for video ram")
            self.add_chunk(CHUNK_VIDEORAM, bank, address, data[:chunksize])
            data = data[chunksize:]
            address += chunksize

    def add_User(self, chunktype: int, data: bytes) -> None:
        if chunktype < 0 or chunktype > 239:
            raise ValueError("user chunk type must be 0 - 239")
        if len(data) > 65535:
            raise ValueError("data too large to fit in a single chunk")
        self.add_chunk(chunktype, data=data)

    def add_Pause(self, code: int) -> None:
        self.add_chunk_nodata(CHUNK_PAUSE, code)

    def add_EOF(self) -> None:
        self.add_chunk_nodata(CHUNK_EOF, 0)

    def add_chunk(self, chunktype: int, bank: int = 0, address: int = 0, data: bytes = b"") -> None:
        try:
            self.loadlist.add_chunk(chunktype, len(data), bank, address)
        except IndexError:
            # loadlist is full, flush everything out and create a new one and put it in there.
            self.flush_ll()
            self.loadlist.add_chunk(chunktype, len(data), bank, address)
        if data:
            self.datachunks.append(bytes(data))

    def add_chunk_nodata(self, chunktype: int, code: int = 0) -> None:
        try:
            self.loadlist.add_chunk(chunktype, code, 0, 0)
        except IndexError:
            # loadlist is full, flush everything out and create a new one and put it in there.
            self.flush_ll()
            self.loadlist.add_chunk(chunktype, code, 0, 0)

    def flush_ll(self) -> None:
        if not self.loadlist.is_empty():
            self.chunks.append(self.loadlist)
            self.chunks.extend(self.datachunks)
            self.loadlist = LoadList()
            self.datachunks.clear()


if __name__ == "__main__":
    try:
        bitmap1 = open("testdata/ME-TITLESCREEN.BIN", "rb").read()
        bitmap2 = open("testdata/DS-TITLESCREEN.BIN", "rb").read()
        palette1 = open("testdata/ME-TITLESCREEN.PAL", "rb").read()
        palette2 = open("testdata/DS-TITLESCREEN.PAL", "rb").read()
    except IOError:
        print("""ERROR: cannot load the demo data files. 
You'll need to put the titlescreen data files from the 'musicdemo' project into the testdata directory.
The musicdemo is on github: https://github.com/irmen/cx16musicdemo
The four files are the two ME- and the two DS- TITLESCREEN.BIN and .PAL files, and are generated by running the makefile in that project.""")
        raise SystemExit

    program = bytearray(3333)
    textdata = b"hello this is a demo text....  \x00"

    mcf = MultiChunkFile()
    for _ in range(4):
        mcf.add_User(42, bytearray(999))
        mcf.add_SystemRam(0, 0x4000, program)
        mcf.add_BankedRam(10, 0xa000, textdata)
    mcf.add_Pause(444)
    for _ in range(4):
        mcf.add_User(42, bytearray(999))
        mcf.add_VideoRam(1, 0xfa00, palette1)
        mcf.add_VideoRam(0, 0, bitmap1)
        mcf.add_Pause(333)
        mcf.add_VideoRam(1, 0xfa00, palette2)
        mcf.add_VideoRam(0, 0, bitmap2)
        mcf.add_Pause(222)
    mcf.add_Pause(111)
    mcf.add_EOF()
    mcf.write("demo.mcf")
    print("Verifying file...")
    mcf2 = MultiChunkFile()
    mcf2.read("demo.mcf")
    assert mcf2.chunks == mcf.chunks
