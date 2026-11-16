macOS build notes (r3Silic0n)
- The app bundle (bundled Java runtime, Quick Look extension DroiD64Preview.appex, OpenCBM, 7zz)
  is not built from this repository; new versions reuse the last signed DroiD64.app and swap
  Contents/app/droid64.jar.
- mkapp.py BASE_APP NEW_JAR VERSION OUTDIR DOCS... copies the app, swaps the jar, sets the
  version in Info.plist and fixes the ad-hoc signature (resource seal + launcher slots).
- 261116.1: the Quick Look extension's typed-commands string (LOAD"$",8 ... LIST) was replaced by
  an empty HTML comment of the same length; its code page hashes, its cdhash in the app's
  CodeResources and the launcher's CodeResources slot were recomputed.
