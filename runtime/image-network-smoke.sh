#!/usr/bin/env bash
# Isolated real Docker network probe; no development checkout in the container.
set -euo pipefail
image=${1:?image required}
root=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
if [[ -n ${DOCKER_CONTEXT:-} ]]; then
  endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
else
  endpoint=${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}
fi
[[ $endpoint == unix://* ]] || { echo 'Local Docker socket required' >&2; exit 2; }
socket=${endpoint#unix://}
socket_gid=$(docker run --rm --mount "type=bind,source=$socket,target=/var/run/docker.sock" --entrypoint stat "$image" -c '%g' /var/run/docker.sock)
image_id=$(docker image inspect --format '{{.Id}}' "$image")
mkdir -p "$root/.datenportal-integrator"
fixture=$(mktemp -d "$root/.datenportal-integrator/image-runtime.XXXXXX")
docker run --rm --network host --user "$(id -u):$(id -g)" --group-add "$socket_gid" \
  --mount "type=bind,source=$socket,target=/var/run/docker.sock" \
  --mount "type=bind,source=$fixture,target=$fixture" -w "$fixture" \
  --mount "type=bind,source=$root/build/classes/java/test,target=/probe-classes,readonly" \
  -e DATENPORTAL_CONTAINER_MODE=true -e "DATENPORTAL_RUNTIME_IMAGE=$image_id" \
  --entrypoint java "$image_id" \
  -cp "/probe-classes:/opt/datenportal-agent/build/libs/datenportal-integrator.jar" \
  ch.so.agi.integrator.ImageRuntimeAcceptance "$fixture"
