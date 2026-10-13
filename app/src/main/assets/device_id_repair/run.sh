#!/system/bin/sh
# One explicit attempt; no polling, automatic retries, or keybox installation.
umask 077
BASE=/data/adb/redmagic_toolbox/device_id_repair
EXPECTED=a5eedbc361c631d50398c6458b0b8cf1b6130fa97454e5ea203ada1c71287768
result() { printf '%s\n' "$1" > "$BASE/status.new" && mv -f "$BASE/status.new" "$BASE/status"; printf '%s\n' "$1"; }
[ "$(id -u)" = 0 ] || exit 1
[ "$(getprop ro.product.model)" = NX809J ] || exit 1
[ -d "$BASE" ] && [ ! -L "$BASE" ] || exit 1
module=/data/adb/modules/autoDeviceID
if [ -d "$module" ] && [ ! -e "$module/disable" ] && [ ! -e "$module/remove" ]; then
    result 'Handled by installed module'; exit 0
fi
boot=$(cat /proc/sys/kernel/random/boot_id) || exit 1
case "$boot" in ''|*[!0-9a-f-]*) exit 1 ;; esac
if [ "$1" = boot ]; then
    [ -f "$BASE/boot_enabled" ] || exit 0
    mkdir "$BASE/boot-$boot" 2>/dev/null || exit 0
fi
# A killed runner leaves this boot-scoped lock in place. Never guess that secure work stopped.
lock="$BASE/operation-$boot"
mkdir "$lock" 2>/dev/null || { printf '%s\n' 'Repair busy or unfinished; reboot before retrying'; exit 0; }
finish() { result "$1"; rmdir "$lock"; exit 0; }
[ "$(sha256sum "$BASE/KmInstallKeybox" | cut -d ' ' -f 1)" = "$EXPECTED" ] || finish 'Binary verification failed'
for library in libminkdescriptor.so libmodem_device_ids.so libcrypto.so; do
    [ -r "/vendor/lib64/$library" ] || finish 'Required vendor library unavailable'
done
cp /vendor/lib64/libminkdescriptor.so "$BASE/libminkdescriptor.so.new" &&
chmod 600 "$BASE/libminkdescriptor.so.new" &&
mv -f "$BASE/libminkdescriptor.so.new" "$BASE/libminkdescriptor.so" || finish 'Dependency setup failed'
result 'Repair running; do not retry until completion'
# Only anonymous outcome markers reach storage/UI. Raw modem identifiers are never logged.
# Preserve the binary's exit code separately from the output filter's exit code.
(
    su -mm -c "LD_LIBRARY_PATH=$BASE:/vendor/lib64 $BASE/KmInstallKeybox Keybox_file Device_ID true true"
    code=$?
    printf '\n__REDMAGIC_EXIT_%s__\n' "$code"
) 2>&1 | awk '
    /^Device info provisioning complete[.]$/ { completed=1 }
    /^__REDMAGIC_EXIT_0__$/ { exited=1 }
    END { if (completed && exited) print "Device info provisioning completed"; else print "Repair failed or completion unverified" }
' > "$lock/outcome"
[ -s "$lock/outcome" ] || exit 1
outcome=$(cat "$lock/outcome")
result "$outcome"
rm -f "$lock/outcome"
rmdir "$lock"
