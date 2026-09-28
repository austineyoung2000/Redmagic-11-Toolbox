#!/system/bin/sh

PATH=/system/bin:/system_ext/bin:/vendor/bin:/product/bin
export PATH

SUPERVISOR_VERSION=2
PACKAGE_NAME="com.elitedarkkaiser.redmagic"
ACCESSIBILITY_COMPONENT="com.elitedarkkaiser.redmagic/com.elitedarkkaiser.redmagic.TriggerAccessibilityService"
WATCHDOG_PROCESS="com.elitedarkkaiser.redmagic:gameplay_watchdog"
WATCHDOG_COMPONENT="com.elitedarkkaiser.redmagic/.GameplayRuntimeWatchdogService"
WATCHDOG_ACTION="com.elitedarkkaiser.redmagic.KEEP_GAMEPLAY_WATCHDOG"
STATE_DIR="/data/adb/redmagic_toolbox"
LOCK_DIR="$STATE_DIR/gameplay_supervisor.lock"
PID_FILE="$LOCK_DIR/pid"
LOG_FILE="$STATE_DIR/gameplay_supervisor.log"
PREVIOUS_LOG_FILE="$STATE_DIR/gameplay_supervisor.previous.log"

mkdir -p "$STATE_DIR" || exit 1
chmod 0700 "$STATE_DIR" 2>/dev/null

rotate_log_if_needed() {
    [ -f "$LOG_FILE" ] || return 0

    size="$(wc -c < "$LOG_FILE" 2>/dev/null)"
    case "$size" in
        ''|*[!0-9]*) return 0 ;;
    esac

    if [ "$size" -ge 65536 ]; then
        mv -f "$LOG_FILE" "$PREVIOUS_LOG_FILE"
    fi
}

log_message() {
    rotate_log_if_needed
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" \
        >> "$LOG_FILE"
}

acquire_lock() {
    if mkdir "$LOCK_DIR" 2>/dev/null; then
        printf '%s\n' "$$" > "$PID_FILE"
        return 0
    fi

    old_pid="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
        return 1
    fi

    rm -rf "$LOCK_DIR"
    mkdir "$LOCK_DIR" 2>/dev/null || return 1
    printf '%s\n' "$$" > "$PID_FILE"
}

release_lock() {
    recorded_pid="$(cat "$PID_FILE" 2>/dev/null)"
    if [ "$recorded_pid" = "$$" ]; then
        rm -rf "$LOCK_DIR"
    fi
}

handle_termination() {
    release_lock
    exit 0
}

accessibility_configured() {
    enabled_services="$(
        su 2000 -c \
            'settings --user 0 get secure enabled_accessibility_services' \
            2>/dev/null
    )"

    case ":$enabled_services:" in
        *":$ACCESSIBILITY_COMPONENT:"*) return 0 ;;
        *) return 1 ;;
    esac
}

watchdog_running() {
    pidof "$WATCHDOG_PROCESS" >/dev/null 2>&1
}

start_watchdog() {
    start_output="$(
        su 2000 -c \
            "am start-foreground-service --user 0 -a $WATCHDOG_ACTION -n $WATCHDOG_COMPONENT" \
            2>&1
    )"
    start_status="$?"

    if [ "$start_status" -ne 0 ]; then
        log_message \
            "framework start failed status=$start_status output=$start_output"
        return "$start_status"
    fi

    return 0
}

acquire_lock || exit 0
trap release_lock EXIT
trap handle_termination INT TERM

log_message "supervisor v$SUPERVISOR_VERSION started pid=$$"

# Package Manager and credential-encrypted settings may not be ready when
# service.d first starts during boot.
sleep 20

missing_package_checks=0
failure_delay=2

while true; do
    # The steady-state path is one cheap procfs-backed process lookup. Avoid
    # Binder/settings/package-manager traffic while the watchdog is healthy.
    if watchdog_running; then
        missing_package_checks=0
        failure_delay=2
        sleep 2
        continue
    fi

    if ! pm path "$PACKAGE_NAME" >/dev/null 2>&1; then
        missing_package_checks=$((missing_package_checks + 1))
        if [ "$missing_package_checks" -ge 20 ]; then
            log_message "package absent; removing orphaned supervisor"
            rm -f "/data/adb/service.d/redmagic_gameplay_supervisor.sh"
            exit 0
        fi
        sleep 15
        continue
    fi
    missing_package_checks=0

    if ! accessibility_configured; then
        failure_delay=2
        sleep 15
        continue
    fi

    log_message "watchdog missing; requesting foreground-service recovery"
    start_watchdog
    sleep 4

    if watchdog_running; then
        log_message "watchdog recovery succeeded"
        failure_delay=2
        continue
    fi

    log_message "watchdog recovery failed; retrying in ${failure_delay}s"
    sleep "$failure_delay"
    failure_delay=$((failure_delay * 2))
    if [ "$failure_delay" -gt 60 ]; then
        failure_delay=60
    fi
done
