 	%import textio ; txt.*
 	main {
 		sub start() {
 			; ATTENTION: uncomment only one problematic line at a time!

 			; Normal string literals, i.e. PETSCII encoding
 			; ---------------------------------------------
 			txt.print("\"")	; fine
 			txt.print("\n")	; fine
 			txt.print("\r")	; fine
 			; txt.print("\\")	; yields CharConversionException
 			; txt.print("xyz\\")	; yields prog8.compiler.AssemblyError

 			; @-strings, i.e. translated into
 			; the alternate character encoding (Screencodes/pokes)
 			; ----------------------------------------------------
 			txt.print(@"\"")	; fine
 			txt.print(@"\n")	; yields CharConversionException
 			; txt.print(@"xyz\n")	; yields prog8.compiler.AssemblyError
 			; txt.print(@"\r")	; yields CharConversionException
 			; txt.print(@"xyz\r")	; yields prog8.compiler.AssemblyError
 			; txt.print(@"\\")	; yields CharConversionException
 			; txt.print(@"\\")	; yields prog8.compiler.AssemblyError

 			; there may be more...
 		}
 	}
