Upgrading from version 8
========================

How to upgrade older programs written for Prog8 version 8 or earlier to version 9.

cx16diskio -> diskio
^^^^^^^^^^^^^^^^^^^^

The ``cx16diskio`` module is gone, just use ``diskio``. The drivenumber is no longer a parameter on all routines.

* replace all imports and references to ``cx16diskio`` with just ``diskio``
* remove all drive number arguments from the calls. If you're not using drive 8, set the correct drive
  with a call to ``diskio.set_drive()``.  Read the active drive from ``diskio.drivenumber``.
* replace load calls that use a ram bank argument by setting the ram bank first using ``cx16.rambank()``
  and then call the load routine normally.


@Pc now ``bool``
^^^^^^^^^^^^^^^^
Parameters and return values passed via the carry status flag (@Pc) now need to be declared as ``bool``.
(Previously also ``ubyte`` was allowed but as the value is just a single bit, this wasn't really correct)


``cbm`` contains kernal calls
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Instead of ``c64``, the CBM compatible kernal calls such as CHROUT, and variables, are now
located in the ``cbm`` module.  You don't have to import this module as it is part of the syslib.

* replace all references such as ``c64.CHROUT`` with the ``cbm`` module instead to fix those undefined symbol errors.


some routines moved to ``sys``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Several routines have been moved to the ``sys`` module rather than being in ``c64`` or ``cx16`` for instance.
An example is ``set_irq()``. If you fixed the previous item above, and still get undefined symbol errors,
the routine is likely now located in the ``sys`` module so simplychange its prefix to ``sys.``


for loop range checking to avoid wrap-around looping
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Modify any for loops that depend on a 'wrap-around' loop where the from value lies
outside of the the loop range (for example: a loop from $fe to $02  which used to loop through $fe, $ff, $00, $01 and $02).
For loops now do a check at the start and skip the whole loop if the start value is already outside of the range.
This is the normal behavior of most other programming languages.


For 9.0 major changes
^^^^^^^^^^^^^^^^^^^^^
- DONE: added min() max() builtin functions
- DONE: rename sqrt16() to just sqrt(), make it accept multiple numeric types. Renamed floats.sqrt() to floats.sqrtf() but you can just use sqrt()
- DONE: abs() now supports multiple datatypes including float. No need to use floats.fabs() anymore.
- DONE: divmod() now supports multiple datatypes.  divmodw() has been removed.


