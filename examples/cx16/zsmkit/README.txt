Prog8 ZSMKIT music player library integration
---------------------------------------------

ZSMKIT:  https://github.com/mooinglemur/zsmkit
(evolution of Zerobyte's ZSOUND, by MooingLemur).  Read the README there!


DEMO1:  LOW-RAM ZSMKIT + STREAMING
----------------------------------
demo1.p8 shows a way to embed the zsmkit blob into the program itself (at $0830).
It then uses the streaming support in zsmkit to load the song from disk as it is played.
It shows some other features such as callbacks and pausing as well.
The way the zsmkit blob is embedded into the program is done by telling prog8 that
the 'main' block of the program has to start at $0830, and the very first command
in that block is not the usual 'start' subroutine but an %asmbinary command to load
and embed the zsmkit blob right there and then.



DEMO2:  HI-RAM ZSMKIT + PRELOADING
----------------------------------
demo2.p8 shows a simpler program that loads a zsmkit blob into upper memory at $8c00
and then also preloads the whole music file into hi-ram. No streaming is used to
play it, it plays everything from ram.
Note that the zsmkit blob used for this is a smaller build as the $0830 one because
this one was configured without streaming support enabled.


CUSTOMIZING ZSMKIT
------------------

Read the README on the zsmkit GitHub repo. It contains a lot of important information,
about how zsmkit works, but also about the various things you have to configure to build a
new library blob to your liking. The example here includes two recently built variants of the blob,
so you don't immediately have to build something yourself, but if you want to enable or disable
streaming support or change the load address you'll have to build one yourself.
See the "alternative builds" chapter.


FUTURE: ZSMKIT V2
-----------------
If all goes well, there will be a zsmkit v2 in the future that has some important changes
that will make it much easier to integrate it into prog8 programs.  Less RAM usage and
a fixed jump table location, among other changes.
