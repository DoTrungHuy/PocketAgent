#!/data/data/com.termux/files/usr/bin/bash
set -eu

SOURCE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
POCKETAGENT_HOME="${POCKETAGENT_HOME:-$HOME/.pocketagent}"
APP_DIR="$POCKETAGENT_HOME/app"
BIN_DIR="${PREFIX:-/data/data/com.termux/files/usr}/bin"
NO_CONFIG=0
UPDATE=0

for arg in "$@"; do
  case "$arg" in
    --no-config) NO_CONFIG=1 ;;
    --update) UPDATE=1 ;;
    *) echo "未知参数：$arg" >&2; exit 2 ;;
  esac
done

case "$POCKETAGENT_HOME" in
  ""|"/"|"$HOME")
    echo "拒绝使用不安全的 POCKETAGENT_HOME：$POCKETAGENT_HOME" >&2
    exit 1
    ;;
esac

if [ -z "${PREFIX:-}" ] || [ ! -x "$(command -v pkg 2>/dev/null || true)" ]; then
  if [ "${POCKETAGENT_ALLOW_NON_TERMUX:-0}" != "1" ]; then
    echo "PocketAgent 首版仅支持 Termux。请从 F-Droid 或 Termux 官方可信渠道安装。" >&2
    exit 1
  fi
fi

arch="$(uname -m)"
case "$arch" in
  aarch64|arm64) ;;
  *)
    echo "警告：当前架构为 $arch，首轮实机只验收 ARM64。"
    ;;
esac

if command -v pkg >/dev/null 2>&1; then
  echo "[1/5] 检查 Termux 软件源和基础环境"
  pkg update -y
  pkg install -y python curl ca-certificates
fi

echo "[2/5] 检查 Python"
python -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 9) else 1)' || {
  echo "需要 Python 3.9 或更高版本。" >&2
  exit 1
}

echo "[3/5] 安装 PocketAgent 程序"
mkdir -p "$APP_DIR/config" "$APP_DIR/web" "$POCKETAGENT_HOME/logs" "$POCKETAGENT_HOME/sessions"
cp "$SOURCE_DIR/app/pocketagent.py" "$APP_DIR/pocketagent.py"
cp "$SOURCE_DIR/app/web/index.html" "$APP_DIR/web/index.html"
cp "$SOURCE_DIR/config/providers.json" "$APP_DIR/config/providers.json"
cp "$SOURCE_DIR/VERSION" "$APP_DIR/VERSION"
cp "$SOURCE_DIR/pocketagent" "$APP_DIR/pocketagent-launcher"
chmod 700 "$APP_DIR/pocketagent.py" "$APP_DIR/pocketagent-launcher"

mkdir -p "$BIN_DIR"
cp "$SOURCE_DIR/pocketagent" "$BIN_DIR/pocketagent"
chmod 700 "$BIN_DIR/pocketagent"

echo "[4/5] 创建工作目录"
mkdir -p "$HOME/PocketAgentWorkspace"
chmod 700 "$POCKETAGENT_HOME"

if [ "$UPDATE" -eq 0 ]; then
  rm -rf "$POCKETAGENT_HOME/source"
  mkdir -p "$POCKETAGENT_HOME/source"
  cp "$SOURCE_DIR/install.sh" "$POCKETAGENT_HOME/source/install.sh"
  cp -R "$SOURCE_DIR/app" "$POCKETAGENT_HOME/source/app"
  cp -R "$SOURCE_DIR/config" "$POCKETAGENT_HOME/source/config"
  cp "$SOURCE_DIR/pocketagent" "$SOURCE_DIR/VERSION" "$POCKETAGENT_HOME/source/"
fi

echo "[5/5] 安装完成"
echo "命令位置：$BIN_DIR/pocketagent"
if [ "$NO_CONFIG" -eq 0 ]; then
  pocketagent configure
else
  echo "已跳过配置。稍后运行 pocketagent configure。"
fi
