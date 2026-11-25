; ---------------------------------------------------------------------------
;  Smart Billing Pro - Windows installer (Inno Setup 6)
;  Developed by KeyKraft.
;
;  Do not compile this file by hand: run make-installer.bat in the project
;  folder. It builds the app with its own Java runtime and passes:
;    /DAppVersion=4.0.0
;    /DAppImageDir=<the jpackage app image folder "SmartBillingPro">
;    /DBindToken=<this build's computer-registration token>
;    /DOutputDir=<folder for the finished setup .exe>
;
;  The installer shows the terms of use (they must be accepted), copies the
;  app, and registers the computer it is installed on: the installed copy
;  will not run if it is copied to another computer.
; ---------------------------------------------------------------------------

#ifndef AppVersion
  #define AppVersion "4.0.0"
#endif
#ifndef AppImageDir
  #error AppImageDir is not set - run make-installer.bat instead of compiling this file directly.
#endif
#ifndef BindToken
  #error BindToken is not set - run make-installer.bat instead of compiling this file directly.
#endif
#ifndef OutputDir
  #define OutputDir "..\..\dist"
#endif

#define AppName "Smart Billing Pro"
#define AppExe "SmartBillingPro.exe"
#define Publisher "KeyKraft"

[Setup]
AppId={{2FC7A773-BAE9-43ED-8897-502138CC33E5}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#Publisher}
AppCopyright=Copyright (c) 2026 {#Publisher}
VersionInfoCompany={#Publisher}
VersionInfoProductName={#AppName}
VersionInfoDescription={#AppName} installer
VersionInfoVersion={#AppVersion}
DefaultDirName={autopf}\SmartBillingPro
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
DisableWelcomePage=no
LicenseFile=..\..\resources\legal\terms.txt
OutputDir={#OutputDir}
OutputBaseFilename=SmartBillingPro-Setup-{#AppVersion}
SetupIconFile=..\app.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
WizardStyle=modern
WizardImageFile=wizard-large.bmp,wizard-large-2x.bmp
WizardSmallImageFile=wizard-small.bmp,wizard-small-2x.bmp
Compression=lzma2/max
SolidCompression=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
MinVersion=10.0
CloseApplications=yes
RestartApplications=no
SetupLogging=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel1=Welcome to {#AppName}
WelcomeLabel2=This will install {#AppName} {#AppVersion}, the offline point of sale by {#Publisher}, on this computer.%n%nEverything it needs is included - no internet connection or other software is required.%n%n{#AppName} will only work on this computer.
WizardLicense=Terms of Use
LicenseLabel=Please read the terms of use before continuing.
LicenseLabel3=You must accept these terms to install {#AppName}.
LicenseAccepted=I &accept the terms of use
LicenseNotAccepted=I &do not accept the terms of use
FinishedHeadingLabel={#AppName} is installed
FinishedLabel={#AppName} has been installed and registered to this computer.%n%nYour shop's data stays on this computer only. A backup is made automatically every day - you can send backups to a USB drive in Tools and Settings.

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "Open {#AppName} now"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; the computer registration is written after the files are copied, so remove it explicitly
Type: files; Name: "{app}\device.lic"
Type: files; Name: "{app}\device.lic.tmp"

[Code]
{ Registers this computer: the installed copy only runs here (see DeviceLock in the app). }
procedure RegisterThisComputer;
var
  ResultCode: Integer;
begin
  WizardForm.StatusLabel.Caption := 'Registering this computer...';
  if not Exec(ExpandConstant('{app}\{#AppExe}'), '--bind-device {#BindToken}', ExpandConstant('{app}'),
              SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    ResultCode := -1;
  if ResultCode <> 0 then
  begin
    Log('Computer registration failed with code ' + IntToStr(ResultCode));
    SuppressibleMsgBox('{#AppName} could not register this computer (code ' + IntToStr(ResultCode) + ').' + #13#10#13#10 +
      'Please run the installer again. If this keeps happening, contact {#Publisher}.', mbError, MB_OK, IDOK);
  end
  else
    Log('Computer registered.');
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
    RegisterThisComputer;
end;
