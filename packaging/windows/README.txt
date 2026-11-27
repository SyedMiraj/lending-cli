PRIME LENDING CLI - USER GUIDE
==============================

The Prime Lending CLI is a command-line tool for calling the Prime Lending
merchant APIs: customer binding, credit assessment, eligibility, invoices,
loan information and settlement.

You don't need to install Java or anything else. Everything the tool needs
is in this folder.


WHAT YOU NEED
-------------
- Windows 10 or Windows 11, 64-bit
- An internet connection. The tool does NOT work over the office LAN.
  Disconnect the LAN and use the internet.


WHAT'S IN THIS FOLDER
---------------------
  now-lending.exe        The program
  install.cmd            Sets up the "now-lending" command (run once)
  uninstall.cmd          Removes the program
  setup.ps1              Used by install.cmd and uninstall.cmd
  vcruntime140.dll       Files the program needs; leave them in this folder
  vcruntime140_1.dll
  config\merchants.json  Your merchant settings and credentials
                         (CONFIDENTIAL: don't share this file or email it on)


1. INSTALL (ONE TIME)
---------------------
  1. Right-click PrimeLendingCLI.zip and choose "Extract All...".
  2. Open the extracted PrimeLendingCLI folder.
  3. Double-click install.cmd.
  4. When you see "Installed to ...", press any key to close the window.

The program is installed for your Windows user only, so you don't need
administrator rights. It is copied to:
  %LOCALAPPDATA%\Programs\PrimeLendingCLI

After installing, you can delete the zip and the extracted folder.


2. RUN
------
  1. Open a NEW Command Prompt or PowerShell window.
     (Press the Windows key, type "cmd", then press Enter.)
  2. Type:   now-lending
  3. Press Enter.

You can run it from any folder.

You can also run it without installing: double-click now-lending.exe in the
extracted folder.


3. USE
------
When it starts, the tool asks you to:
  1. Select the merchant (for example 1 = Hishabee, 2 = Wegro)
  2. Select the loan type (1 = Retail, 2 = SME)

Then it shows the Retail menu:

  1. Customer Binding       5. Submit Invoice
  2. Questions List         6. Loan Information
  3. Credit Assessment      7. Settlement Request
  4. Check Eligibility      8. Settlement
  9. Switch Merchant        0. Exit

Type a number and press Enter.

Tips:
- Each question shows a default value in [brackets]. Press Enter to keep
  it, or type a new value.
- Values from earlier steps are filled in for you in later steps: account
  number, answer response ID, invoice number, amount and loan ID. The usual
  order is 1 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8.
- After each request, the tool prints the HTTP status and the response from
  the server.
- To quit, choose 0, or press Ctrl+C.


4. UPDATE TO A NEW VERSION
--------------------------
Extract the new zip and double-click its install.cmd. It replaces the old
version, including the settings in config\merchants.json.


5. UNINSTALL
------------
  1. Open File Explorer.
  2. Paste this into the address bar and press Enter:
     %LOCALAPPDATA%\Programs\PrimeLendingCLI
  3. Double-click uninstall.cmd.

This removes the program and the "now-lending" command.


TROUBLESHOOTING
---------------
"now-lending is not recognized as an internal or external command"
    Open a NEW terminal window after installing. Windows that were already
    open don't pick up the new command. If it still fails, run install.cmd
    again.

"Windows protected your PC" (blue SmartScreen window)
    Click "More info", then "Run anyway". This appears because the program
    isn't signed by a publisher yet.

install.cmd shows an error, or closes straight away
    Your PC may block PowerShell scripts. You can still use the tool by
    double-clicking now-lending.exe in the extracted folder. Ask your IT team if
    you need the "now-lending" command.

"Connection failed. Make sure LAN is disconnected"
    Disconnect from the office LAN (cable or LAN Wi-Fi), connect to the
    internet, then try again.

"Could not load config ..."
    The file config\merchants.json is missing. It must be inside a folder
    named "config" next to now-lending.exe. Extract the zip again, or ask us for
    a new package.

"Token request failed (HTTP 401)" or other login errors
    The credentials in config\merchants.json are wrong or have expired.
    Contact us for an updated package.

Bangla text looks garbled
    The tool switches the window to UTF-8 by itself. If text still looks
    wrong, run this first and then start the tool again:
        chcp 65001


SUPPORT
-------
When you report a problem, send a screenshot of the window, including the
HTTP status and the response.
Don't send the config\merchants.json file.
