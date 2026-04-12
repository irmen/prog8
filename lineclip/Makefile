# Line clipping library and test programs
CC      = gcc
CFLAGS  = -Wall -Wextra -O2
LDFLAGS =
LIBS    =
SDL_CFLAGS = $(shell pkg-config --cflags sdl3 2>/dev/null)
SDL_LIBS   = $(shell pkg-config --libs sdl3 2>/dev/null || echo "-lSDL3")

# Sources
LIB_SRCS  = lineclip.c
CS_SRCS   = test_clip.c
VIS_SRCS  = clip_visual.c

# Targets
all: test_clip clip_visual

test_clip: $(CS_SRCS) $(LIB_SRCS)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(CS_SRCS) $(LIB_SRCS) $(LIBS)

clip_visual: $(VIS_SRCS) $(LIB_SRCS)
	$(CC) $(CFLAGS) $(SDL_CFLAGS) $(LDFLAGS) -o $@ $(VIS_SRCS) $(LIB_SRCS) $(SDL_LIBS)

clean:
	rm -f test_clip clip_visual

.PHONY: all clean
