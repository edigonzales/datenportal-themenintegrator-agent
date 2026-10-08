#!/usr/bin/env bash
set -euo pipefail
runtime_dir=/var/lib/integrator
rm -f "$runtime_dir/ready"
mkdir -p "$GRADLE_USER_HOME" "$runtime_dir/jobs" "$runtime_dir/warmup/shared/bin"
if [[ ! -f "$GRADLE_USER_HOME/.integrator-image" ]]; then
  cp -a /opt/datenportal/offline-bundle/gradle-user-home/. "$GRADLE_USER_HOME/"
  rm -rf "$GRADLE_USER_HOME/daemon"
  printf '%s\n' "$INTEGRATOR_RUNTIME_KEY" > "$GRADLE_USER_HOME/.integrator-image"
fi
[[ "$(cat "$GRADLE_USER_HOME/.integrator-image")" == "$INTEGRATOR_RUNTIME_KEY" ]] || {
  echo 'Gradle-Cache gehoert zu einem anderen Image.' >&2; exit 1;
}
# Use the canonical wrapper, but no business build or publication configuration.
cp /opt/integrator-topic/gradlew "$runtime_dir/warmup/gradlew"
cp -a /opt/integrator-topic/gradle "$runtime_dir/warmup/"
cp /opt/integrator-topic/shared/bin/gradlew-java17.sh "$runtime_dir/warmup/shared/bin/"
printf "rootProject.name = 'integrator-warmup'\n" > "$runtime_dir/warmup/settings.gradle"
: > "$runtime_dir/warmup/build.gradle"
chown -R jenkins:jenkins "$runtime_dir"
exec su -s /bin/bash jenkins -c 'exec bash /opt/integrator-runtime/gretl-wait.sh'
