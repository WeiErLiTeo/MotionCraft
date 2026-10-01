#!/usr/bin/env bash
set -e

DEST_DIR="/tmp/apk_server"
mkdir -p "$DEST_DIR"

APK_SRC=$(find app/build/outputs/apk/release/ -name "*.apk" 2>/dev/null | head -n 1)
if [ -z "$APK_SRC" ]; then
    APK_SRC=$(find app/build/outputs/apk/ -name "*release*.apk" 2>/dev/null | head -n 1)
fi

if [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" "$DEST_DIR/MotionCraft-v1.2.0-release.apk"
    cp "$APK_SRC" "$DEST_DIR/MotionCraft.apk"
    echo "[OK] APK: $APK_SRC -> $DEST_DIR/MotionCraft-v1.2.0-release.apk"
    
    echo "Uploading to temp.sh..."
    TEMP_URL=$(curl -s -F "file=@$APK_SRC" https://temp.sh/upload || true)
    if [[ "$TEMP_URL" =~ ^http ]]; then
        echo "TEMP_SH_URL=$TEMP_URL" > /tmp/apk_download_url.txt
        echo "[SUCCESS] Direct URL: $TEMP_URL"
    else
        echo "Uploading to bashupload..."
        BASH_URL=$(curl -s -T "$APK_SRC" https://bashupload.com/MotionCraft-v1.2.0-release.apk | grep -o 'https://bashupload.com/[^ ]*' | head -n 1 || true)
        if [[ "$BASH_URL" =~ ^http ]]; then
            echo "BASH_URL=$BASH_URL" > /tmp/apk_download_url.txt
            echo "[SUCCESS] Direct URL: $BASH_URL"
        fi
    fi
else
    echo "[ERROR] Release APK not found"
fi

if ! ss -tlpn 2>/dev/null | grep -q ":3000 "; then
    nohup python3 -m http.server 3000 --directory "$DEST_DIR" > /tmp/http.log 2>&1 &
    sleep 1
fi
