#!/usr/bin/env bash
set -euo pipefail
cd /var/lib/integrator/warmup
timeout --kill-after=10s 300s bash shared/bin/gradlew-java17.sh --daemon --console=plain help \
  > /var/lib/integrator/warmup.log 2>&1
touch /var/lib/integrator/ready
exec sleep infinity
