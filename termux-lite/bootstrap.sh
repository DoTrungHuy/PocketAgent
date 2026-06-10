#!/data/data/com.termux/files/usr/bin/bash
set -eu

# Local source-tree mode is used before the first public release.
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/install.sh" ]; then
  exec bash "$SCRIPT_DIR/install.sh" "$@"
fi

RELEASE_URL="${AGENTPAD_RELEASE_URL:-}"
RELEASE_SHA256="${AGENTPAD_RELEASE_SHA256:-}"

if [ -z "$RELEASE_URL" ] || [ -z "$RELEASE_SHA256" ]; then
  cat >&2 <<'EOF'
AgentPad 尚未发布公开安装包。
请先把完整源码目录传到平板，然后在目录内执行：
  bash install.sh

正式发布后，本脚本会下载并校验发布包，而不是直接执行未经校验的远程脚本。
EOF
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT INT TERM
archive="$tmp_dir/agentpad.tar.gz"

echo "正在下载 AgentPad 发布包..."
curl --fail --location --proto '=https' --tlsv1.2 "$RELEASE_URL" -o "$archive"
actual="$(sha256sum "$archive" | awk '{print $1}')"
if [ "$actual" != "$RELEASE_SHA256" ]; then
  echo "SHA256 校验失败，已停止安装。" >&2
  exit 1
fi

mkdir -p "$tmp_dir/source"
tar -xzf "$archive" -C "$tmp_dir/source" --strip-components=1
exec bash "$tmp_dir/source/install.sh" "$@"
