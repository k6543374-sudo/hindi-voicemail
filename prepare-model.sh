#!/bin/sh
set -eu
mkdir -p assets
curl -fL https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip -o model.zip
printf '%s  %s\n' '7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48' model.zip | sha256sum -c -
unzip -q -o model.zip -d assets
printf 'hindi-v1' > assets/vosk-model-small-hi-0.22/uuid
rm model.zip
