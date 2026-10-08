#!/usr/bin/env bash
set -euo pipefail
job_dir=$1
limit=$2
work=$3
wrapper=$4
shift 4
exec 9>/var/lib/integrator/build.lock
flock -w "$limit" 9 || exit 75
printf '%s\n' "$job_dir" > /var/lib/integrator/active
finish() {
  local code=$?
  printf '%s\n' "$code" > "$job_dir/exit-code.tmp"
  mv "$job_dir/exit-code.tmp" "$job_dir/exit-code"
}
trap finish EXIT
cd "$work"
set +e
timeout --kill-after=10s "${limit}s" bash "$wrapper" "$@" > "$job_dir/task.log" 2>&1
code=$?
set -e
if [[ "$code" == 124 || "$code" == 137 ]]; then
  timeout --kill-after=5s 15s bash "$wrapper" --stop >> "$job_dir/task.log" 2>&1 || true
  if pgrep -u jenkins -f '[o]rg.gradle.launcher.daemon.bootstrap.GradleDaemon' >/dev/null; then
    echo 'Gradle-Abbruch nicht bestaetigt; Runtime muss neu starten.' >> "$job_dir/task.log"
    exit 76
  fi
fi
cat "$job_dir/task.log"
exit "$code"
