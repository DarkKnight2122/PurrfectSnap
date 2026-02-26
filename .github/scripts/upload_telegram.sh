#!/bin/bash

# PurrfectSnap "Elite" Telegram Notifier
# Version: 3.1.0 (Hardened Edition)

TOKEN=$1
CHAT_ID=$2
BUILD_STATUS=$3
REPO=$4
RUN_ID=$5
EXTERNAL_SHA=$6
EXTERNAL_BRANCH=$7

# Identifiers
GIT_HASH=${EXTERNAL_SHA:0:7}
BRANCH_NAME="$EXTERNAL_BRANCH"
AUTHOR=$(git log -1 --pretty=%an || echo "Unknown")

# --- Helper: HTML Escaper ---
escape_html() {
    echo "$1" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g; s/"/\&quot;/g; s/'"'"'/\&#39;/g'
}

# Determine Type & Icon
if [[ "$BRANCH_NAME" == "a9" ]]; then
    TYPE_ICON="🏛️"
    TYPE_LABEL="ANDROID 9 STABILIZATION"
elif [[ "$BRANCH_NAME" == "test" ]]; then
    TYPE_ICON="🧪"
    TYPE_LABEL="SANDBOX TESTING"
else
    TYPE_ICON="⚙️"
    TYPE_LABEL="DEV BUILD"
fi

# Extract Dynamic Changelog (Last 5 commits)
DYNAMIC_CHANGELOG=$(git log -n 5 --pretty=format:"• %s")
ESCAPED_CHANGELOG=$(escape_html "$DYNAMIC_CHANGELOG")

# Create Message Header
MESSAGE="<b>$TYPE_ICON PurrfectSnap Elite | $BRANCH_NAME</b>
━━━━━━━━━━━━━━━━
📌 <b>Status:</b> $BUILD_STATUS
🏗️ <b>Type:</b> <code>$TYPE_LABEL</code>
🆔 <b>Build:</b> <code>#$GIT_HASH</code>
👤 <b>Author:</b> $(escape_html "$AUTHOR")
━━━━━━━━━━━━━━━━
📝 <b>Recent Changes:</b>
$ESCAPED_CHANGELOG
━━━━━━━━━━━━━━━━
📂 <b>Assets & Mirrors:</b>"

# Search for APKs in multiple possible locations
SEARCH_PATHS=("all-apks" "app/build/outputs/apk")
FOUND_APKS=""

for path in "${SEARCH_PATHS[@]}"; do
    if [ -d "$path" ]; then
        FOUND_APKS+="$(find "$path" -name "*.apk" -type f 2>/dev/null | grep -v "unsigned")"$'\n'
    fi
done

if [ -z "$(echo -n "$FOUND_APKS" | tr -d '[:space:]')" ]; then
    MESSAGE+="
⚠️ No APK assets discovered."
else
    while read -r apk; do
        [ -z "$apk" ] && continue
        
        FILENAME=$(basename "$apk")
        FILESIZE_BYTES=$(stat -c%s "$apk")
        # Use awk for division to avoid 'bc' dependency
        FILESIZE_MB=$(awk "BEGIN {printf \"%.1f\", $FILESIZE_BYTES/1048576}")
        
        # Architecture detection
        if [[ "$FILENAME" == *"arm64-v8a"* || "$FILENAME" == *"armv8"* ]]; then ARCH="ARM64"
        elif [[ "$FILENAME" == *"armeabi-v7a"* || "$FILENAME" == *"armv7"* ]]; then ARCH="ARMv7"
        else ARCH="Universal"
        fi

        # Pro Renaming
        PRO_NAME="PurrfectSnap_${BRANCH_NAME}_${GIT_HASH}_${ARCH}.apk"
        cp "$apk" "./$PRO_NAME"
        apk_target="./$PRO_NAME"

        echo "📤 Uploading $PRO_NAME..."

        # Mirror Strategy (Try multiple to ensure delivery)
        MIRROR_URL=$(curl -s https://bashupload.com/ -T "$apk_target" | grep -o 'https://bashupload.com/[^ ]*' | head -n 1 | tr -d '\r\n')
        
        if [ -z "$MIRROR_URL" ]; then
             # Fallback to catbox
             MIRROR_URL=$(curl -s -F "reqtype=fileupload" -F "fileToUpload=@$apk_target" https://catbox.moe/user/api.php | tr -d '\r\n')
        fi

        # Append to message
        if [ -n "$MIRROR_URL" ] && [[ "$MIRROR_URL" == "http"* ]]; then
            MESSAGE+="
📦 <code>$ARCH</code> ($FILESIZE_MB MB)
└ <a href=\"$MIRROR_URL\">Download APK</a>"
        else
            MESSAGE+="
📦 <code>$ARCH</code> ($FILESIZE_MB MB)
└ ⚠️ Mirror failed."
        fi

        # Direct Telegram Upload (if size < 50MB)
        if [ "$FILESIZE_BYTES" -lt 50000000 ]; then
            curl -s -F "chat_id=$CHAT_ID" -F "document=@$apk_target" \
                 -F "caption=📦 $ARCH Build (#$GIT_HASH)" \
                 -F "parse_mode=HTML" \
                 "https://api.telegram.org/bot$TOKEN/sendDocument" > /dev/null
        fi
    done <<< "$FOUND_APKS"
fi

MESSAGE+="
━━━━━━━━━━━━━━━━
🛠 <a href=\"https://github.com/$REPO/actions/runs/$RUN_ID\">View Execution Log</a>"

# Final Dispatch
curl -s -X POST "https://api.telegram.org/bot$TOKEN/sendMessage" \
    -d "chat_id=$CHAT_ID" \
    --data-urlencode "text=$MESSAGE" \
    -d "parse_mode=HTML" \
    -d "disable_web_page_preview=true" || exit 0
