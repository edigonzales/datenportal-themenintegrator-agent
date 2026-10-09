#!/usr/bin/env bash
set -euo pipefail
: "${IMAGE:?}" "${AGENT_VERSION:?}" "${GITHUB_SHA:?}"
[[ $AGENT_VERSION =~ ^0\.1\.[0-9]+$ ]] || exit 2
manifest=$(mktemp)
errors=$(mktemp)
trap 'rm -f "$manifest" "$errors"' EXIT
exists() {
  if docker buildx imagetools inspect --raw "$1" > "$manifest" 2> "$errors"; then return 0; fi
  # Network/authentication failures must never be interpreted as an absent tag.
  if grep -Eqi 'manifest unknown|: not found$|no such manifest' "$errors"; then return 1; fi
  cat "$errors" >&2
  exit 1
}
verify_version() {
  local reference=$1 arch revision version digest platform_reference
  docker buildx imagetools inspect --raw "$reference" > "$manifest"
  for arch in amd64 arm64; do
    # Resolve the platform manifest first. Older Docker CLIs cannot select a
    # platform in image inspect; inspecting a shared tag can select the host one.
    digest=$(jq -er --arg arch "$arch" '[.manifests[] | select(.platform.os == "linux" and .platform.architecture == $arch)] | if length == 1 then .[0].digest else error("Expected exactly one platform manifest") end' "$manifest")
    [[ $digest =~ ^sha256:[a-f0-9]{64}$ ]] || { echo 'Invalid platform manifest digest' >&2; exit 1; }
    platform_reference="$IMAGE@$digest"
    docker pull --platform "linux/$arch" "$platform_reference" >&2
    revision=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$platform_reference")
    version=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$platform_reference")
    [[ $revision == "$GITHUB_SHA" && $version == "$AGENT_VERSION" ]] || { echo 'Existing version belongs to different build metadata; refusing overwrite.' >&2; exit 1; }
    bash runtime/image-smoke.sh "$platform_reference" "linux/$arch"
  done
}
case "${1:-}" in
  version)
    if ! exists "$IMAGE:$AGENT_VERSION"; then
      docker buildx build --push --platform linux/amd64,linux/arm64 -f runtime/Dockerfile \
        --build-arg "AGENT_VERSION=$AGENT_VERSION" --build-arg "VCS_REF=$GITHUB_SHA" \
        -t "$IMAGE:$AGENT_VERSION" .
    fi
    verify_version "$IMAGE:$AGENT_VERSION"
    ;;
  verify) verify_version "$IMAGE:$AGENT_VERSION" ;;
  latest)
    # Called under the workflow's promotion lock. Compare release numbers, not job order.
    if exists "$IMAGE:latest"; then
      docker pull --platform linux/amd64 "$IMAGE:latest" >&2
      current=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$IMAGE:latest")
      [[ $current =~ ^0\.1\.[0-9]+$ ]] || { echo 'Unexpected latest version; refusing promotion.' >&2; exit 1; }
      if (( ${current##*.} >= ${AGENT_VERSION##*.} )); then exit 0; fi
    fi
    # The immutable version was checked by the preceding job; promote that exact digest.
    digest=$(docker buildx imagetools inspect "$IMAGE:$AGENT_VERSION" --format '{{.Manifest.Digest}}')
    [[ $digest =~ ^sha256:[a-f0-9]{64}$ ]] || exit 1
    docker buildx imagetools create -t "$IMAGE:latest" "$IMAGE@$digest"
    actual=$(docker buildx imagetools inspect "$IMAGE:latest" --format '{{.Manifest.Digest}}')
    [[ $actual == "$digest" ]]
    ;;
  *) exit 2 ;;
esac
