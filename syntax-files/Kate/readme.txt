Prog8 Syntax Highlighting for Kate
==================================

This directory contains syntax highlighting definition files for Kate,
the KDE Advanced Text Editor (version 6.x / KDE Plasma 6).


INSTALLATION FOR KATE 6.X (Standalone Application)
--------------------------------------------------

For the standalone Kate application (most common installation):

Method 1: User Directory (Recommended)
1. Copy prog8.xml to Kate's syntax directory:
   
   mkdir -p ~/.local/share/org.kde.syntax-highlighting/syntax/
   cp prog8.xml ~/.local/share/org.kde.syntax-highlighting/syntax/

2. Restart Kate

Method 2: System-wide Installation (requires root)
1. Copy prog8.xml to the system syntax directory:
   
   sudo cp prog8.xml /usr/share/org.kde.syntax-highlighting/syntax/

2. Restart Kate


INSTALLATION FOR KATEPART (Embedded Editor)
-------------------------------------------

If you're using Kate as part of another application (e.g., KDevelop):

   mkdir -p ~/.local/share/katepart6/syntax/
   cp prog8.xml ~/.local/share/katepart6/syntax/


VERIFY INSTALLATION
-------------------
To verify the syntax file is recognized by Kate:

1. Open Kate
2. Go to: Tools → Highlighting
3. Look for "Prog8" under the "Scripts" section


FEATURES
--------
- Keywords (if, for, while, sub, return, break, continue, etc.)
- All if_* condition codes (if_z, if_nz, if_cs, if_cc, etc.)
- Compiler directives (%import, %zeropage, %asm, %ir, %option, etc.)
- Attributes (@zp, @shared, @align64, @dirty, @nosplit, etc.)
- Data types (byte, word, ubyte, uword, long, float, str, etc.)
- Pointer types (^^byte, ^^word, ^^ubyte, etc.)
- Built-in functions (peek, poke, push, pop, rol, ror, etc.)
- Encoding prefixes (petscii:, iso:, cp437:, atascii:, etc.)
- Comments (line ; and block /* */)
- Numbers (decimal, hexadecimal $, binary %)
- Strings and character literals


FILE FORMAT
-----------
The syntax definition follows the Kate 6.x XML format.
For more information, see:
https://docs.kde.org/stable6/en/kate/katepart/highlighting.html


TROUBLESHOOTING
---------------
If the syntax doesn't appear after installation:

1. Check for XML errors:
   xmllint --noout ~/.local/share/org.kde.syntax-highlighting/syntax/prog8.xml

2. Clear Kate's syntax cache:
   rm -rf ~/.cache/kate*
   
3. Restart Kate completely

4. Verify the file is in the correct location:
   ls -la ~/.local/share/org.kde.syntax-highlighting/syntax/prog8.xml


LICENSE
-------
This syntax definition is licensed under GPL-3.0.
