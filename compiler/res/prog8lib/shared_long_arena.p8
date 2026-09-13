; -- Long-sized arena / bump allocator for m68k and virtual targets.
;    Manages one static memory slab at a time, provided by the caller via memory().
;    Calling init() again replaces the previously tracked slab.
;    Allocations are made by bumping a pointer forward. Individual
;    deallocations are not supported; use reset() to free everything at once.
;    Sizes are long values and odd sizes are rounded up to an even address.

%option ignore_unused

arena {
    private pointer @shared base
    private pointer @shared next
    private pointer @shared end

    sub init(pointer base_addr, long size) {
        ; -- initialize the arena over the range [base_addr, base_addr + size)
        base = base_addr
        next = base_addr
        end = base_addr + size
    }

    sub alloc(long size) -> pointer {
        ; -- allocate size bytes from the arena. Returns 0 if the slab is exhausted.
        ;    Odd sizes are rounded up to an even number so word/long/struct accesses stay aligned.
        if size == 0
            return 0
        if (size & 1) != 0
            size++
        if next + size > end
            return 0
        pointer result = next
        next += size
        return result
    }

    sub reset() {
        ; -- reset the arena so the slab can be reused from the start
        next = base
    }
}
