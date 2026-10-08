#!/usr/bin/env bash
set -euo pipefail
exec 9>/var/lib/integrator/build.lock
flock -w "$1" 9 || exit 76
[[ -f /var/lib/integrator/active ]] || exit 0
job_dir=$(cat /var/lib/integrator/active)
[[ "$job_dir" == /var/lib/integrator/jobs/* && -f "$job_dir/exit-code" ]] || exit 76
code=$(cat "$job_dir/exit-code")
[[ "$code" =~ ^[0-9]{1,3}$ ]] || exit 76
[[ "$code" -le 255 && "$code" -ne 76 ]] || exit 76
