Here is a ".sublime-syntax" syntax file for Sublime Text 4, and 'bat'.
Format: see https://www.sublimetext.com/docs/syntax.html

INSTALLATION FOR SUBLIME TEXT 4
-------------------------------

Method 1: User Package (Recommended)
1. Open Sublime Text 4
2. Go to: Preferences → Browse Packages...
3. Open the "User" folder
4. Copy Prog8.sublime-syntax to that folder
5. Restart Sublime Text (or the syntax should appear immediately)

Method 2: Dedicated Package
1. Open Sublime Text 4
2. Go to: Preferences → Browse Packages...
3. Create a new folder called "Prog8"
4. Copy Prog8.sublime-syntax into that folder
5. Restart Sublime Text

The syntax will be automatically available for .p8 and .prog8 files.
You can also manually select it via: View → Syntax → Prog8


INSTALLATION FOR BAT (COMMAND LINE UTILITY)
-------------------------------------------

Bat is a cat clone with syntax highlighting:
https://github.com/sharkdp/bat

To install this syntax:

1. Create the syntax directory if it doesn't exist:
   mkdir -p "$(bat --config-dir)/syntaxes"

2. Copy Prog8.sublime-syntax to that directory:
   cp Prog8.sublime-syntax "$(bat --config-dir)/syntaxes/"

3. Rebuild the syntax cache:
   bat cache --build

4. Verify the syntax is available:
   bat --list-languages | grep -i prog8

After installation, bat will automatically use the Prog8 syntax
for .p8 and .prog8 files.


OLDER VERSION
-------------
The older "tmLanguage" syntax definition file, where this sublime-syntax version is based on, can still be found here:

https://github.com/akubiczek/Prog8-TmLanguage-VsCode/tree/master/sublime3
