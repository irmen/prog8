# Multipurpose Chunked File

## Goals

- meant for the Commander X16 system, depends on working MACPTR kernal call for fast loading.
- single file, possibly megabytes in size, that contains various kinds of data to be loaded or streamed into different locations.
- simple file format with few special cases.
- no random access, only sequential reading/streaming.
- simple user code that doesn't have to bother with the I/O at all.
- a few chunk types that can be handled automatically to load data into system ram, banked ram, or video ram.
- custom chunk types for flexibility or extensibility.

Theoretical optimal chunk size is 512 bytes but actual size may be different. (In practice there seems to be no significant speed impact)

MCF files are meant to be created using a tool on PC, and only being read on the X16.
A Python tool is provided to create a demo MCF file.
A proof of concept Prog8 library module and example program is provided to consume that demo MCF file on the X16.


## File Format

This is the MCF file format:

| Chunk      | size (bytes) |
|------------|--------------|
| LoadList   | 256          |
| Data       | 1 - 65535    |
| Data       | 1 - 65535    |
| ...        |              |
| LoadList   | 256          |
| Data       | 1 - 65535    |
| Data       | 1 - 65535    |
| ...        |              |

and so on.
There is no limit to the number of chunks and the size of the file, as long as it fits on the disk.


### LoadList chunk

This chunk is a list of what kinds of chunks occur in the file after it.
It starts with a small identification header:

| data    | meaning             |
|---------|---------------------|
| 2 bytes | 'LL' (76, 76)       |
| byte    | version (1 for now) |
| byte    | reserved (0)        |

Then a sequence of 1 or more chunk specs (6 bytes each), as long as it still fits in 256 bytes:

| data                 | meaning                                    |
|----------------------|--------------------------------------------|
| byte                 | chunk type                                 |
| word (little-endian) | chunk size                                 |
| byte                 | bank number (used for some chunk types)    |
| word (little-endian) | memory address (used for some chunk types) |

Total 6 bytes per occurrence. Any remaining unused bytes in the 256 bytes LoadList chunk are to be padded with byte 255.
If there are more chunks in the file than fit in a single loadlist, we simply add another loadlist block and continue there.
(The file only ends once an End Of File chunk type is encountered in a loadlist.)


### Chunk types

| chunk type | meaning                                                                                                                                                                                                                                         |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0 - 239    | custom chunk types. See below.                                                                                                                                                                                                                  |
| 240 - 248  | reserved for future system chunk types.                                                                                                                                                                                                         |
| 249        | dummy chunk: read a chunk of the specified number of bytes but don't do anything with it. Useful to realign the file I/O on disk block size.                                                                                                    |
| 250        | bonk ram load: use banknumber + address to set the Cartridge RAM ('bonk' RAM) bank and load address and loads the chunk there, then continue streaming. Note this is slower than other types of ram. Rquires 1 page of user chosen buffer area. |
| 251        | system ram load: use banknumber + address to set the RAM bank and load address and loads the chunk there, then continue streaming.                                                                                                              |
| 252        | video ram load: use banknumber + address to set the Vera VRAM bank (hi byte) and load address (mid+lo byte) and loads the chunk into video ram there, then continue streaming.                                                                  |
| 253        | pause streaming. Returns from stream routine with pause status: Carry=clear. And reg.r0=size. until perhaps the program calls the stream routine again to resume.                                                                               |
| 254        | end of file. Closes the file and stops streaming: returns from stream routine with exit status: Carry=set.                                                                                                                                      |
| 255        | ignore this byte. Used to pad out the loadlist chunk to 256 bytes.                                                                                                                                                                              |


### Custom chunk types (0 - 239)

When such a custom chunk type is encountered, a user routine is called to get the load address for the chunk data,
then the chunk is loaded into that buffer, and finally a user routine is called to process the chunk data.
The size can be zero, so that the chunk type acts like a simple notification flag for the main program to do something.

The first routine has the following signature:

**get_buffer()**:
    Arguments: reg.A = chunk type, reg.XY = chunksize.
    Returns: Carry flag=success (set = fail, clear = ok), ram bank in reg.A, memory address in reg.XY.

The second routine has the following signature:

**process_chunk()**:
    Arguments: none (save them from the get_buffer call if needed).
    Returns: Carry flag=success (set = fail, clear = ok).

These routines are provided to the streaming routine as callback addresses (ram bank number + address to call).
If any of these routines returns Carry set (error status) the streaming routine halts, otherwise it keeps on going.
The streaming continues until an End of File chunk type is encountered in the loadlist.
