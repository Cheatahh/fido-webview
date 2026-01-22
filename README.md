# fido-webview

hello!
if you stumbled across this repository, it is probably for one of two reasons:
- you figured that android currently does not implement fido via nfc for browsers (which sucks)
- you are just checking out random repositories because you are bored ;)

at my company we have exactly the issue described in #1. 

i have built a prototype where i did get fido via nfc to work in a custom webview for the microsoft login flow. this repository aims to properly build a cordova plugin allowing this to be used as an embedded application. but you can also just take the code and build a native android app, depends on your needs.

our toolchain requires cordova plugins to be pulled from some git server and i was too lazy to add a local one. thats why this repo is here.
note that im building this on work time & for work, while also being the only developer. so dont expect this repository to be super clean ;)

anyway once this project is done ill properly maintain the repo
