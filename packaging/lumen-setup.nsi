; Single-file setup for Lumen Player.
; It unpacks the portable bundle to a temp folder and hands over to Lumen's own animated
; installer ("Install Lumen.exe"), which does the per-user install. No admin rights needed.
; Build: makensis -DVERSION=1.0.0 -DSTAGE=build/stage/Lumen -DOUT=build/Lumen-Setup-1.0.0.exe packaging/lumen-setup.nsi
; Silent: Lumen-Setup.exe /S   (Lumen's updater adds --update --relaunch "<video>")

Unicode true
ManifestDPIAware true
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 64
!include LogicLib.nsh
!include FileFunc.nsh

Name "Lumen Player"
Caption "Lumen Player ${VERSION}"
OutFile "${OUT}"
Icon "${__FILEDIR__}/../launcher/lumen.ico"
BrandingText "Lumen Player ${VERSION}"
SubCaption 3 " "
XPStyle on
AutoCloseWindow true
ShowInstDetails nevershow
InstallDir "$TEMP"
Page instfiles

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "Lumen Player"
VIAddVersionKey "FileDescription" "Lumen Player Setup"
VIAddVersionKey "CompanyName" "Sara Aminpour"
VIAddVersionKey "LegalCopyright" "GPL-3.0-or-later"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"

Section
  InitPluginsDir
  DetailPrint "Unpacking Lumen…"
  SetOutPath "$PLUGINSDIR\Lumen"
  File /r "${STAGE}/*"
  SetOutPath "$TEMP"            ; leave the temp folder so NSIS can delete it afterwards
  HideWindow
  ${GetParameters} $R0           ; pass through e.g. Lumen's own --update --relaunch "<video>"
  ${If} ${Silent}
    ExecWait '"$PLUGINSDIR\Lumen\Install Lumen.exe" --auto $R0' $0
  ${Else}
    ExecWait '"$PLUGINSDIR\Lumen\Install Lumen.exe" $R0' $0
  ${EndIf}
  SetErrorLevel $0
SectionEnd
