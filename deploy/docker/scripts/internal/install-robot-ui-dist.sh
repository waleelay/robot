#!/usr/bin/env sh
# 内部脚本：安装并清理 Robot UI 静态资源。
set -eu

source_dir=$1
target_dir=$2
install_mode=${3:-skip_existing}
retention_days=${4:-${ROBOT_UI_ASSET_RETENTION_DAYS:-7}}

if [ ! -d "$source_dir" ]; then
  echo "missing robot-ui dist directory: $source_dir" >&2
  exit 1
fi
if [ ! -f "$source_dir/index.html" ]; then
  echo "missing robot-ui entry: $source_dir/index.html" >&2
  exit 1
fi
case "$retention_days" in
  ''|*[!0-9]*)
    echo "invalid robot-ui asset retention days: $retention_days" >&2
    exit 1
    ;;
esac

if [ "$install_mode" != "overwrite" ] && [ -d "$target_dir" ] && [ "$(find "$target_dir" -mindepth 1 -maxdepth 1 | head -n 1)" ]; then
  echo "skip existing $target_dir"
  exit 0
fi

mkdir -p "$target_dir"
if [ "$install_mode" = "overwrite" ] && [ -f "$target_dir/index.html" ]; then
  # 在线页面可能仍引用上一版懒加载资源：先合并资源，最后原子切换入口。
  find "$source_dir" -mindepth 1 -maxdepth 1 ! -name index.html \
    -exec cp -R {} "$target_dir"/ \;
  cp "$source_dir/index.html" "$target_dir/.index.html.next"
  mv "$target_dir/.index.html.next" "$target_dir/index.html"

  # 当前版本刚复制的文件会刷新 mtime；只清理超过宽限期的历史 JS/CSS。
  for asset_dir in "$target_dir/static/js" "$target_dir/static/css"; do
    if [ -d "$asset_dir" ]; then
      find "$asset_dir" -type f -mtime "+$retention_days" -delete
    fi
  done
else
  cp -R "$source_dir"/. "$target_dir"/
fi

echo "installed robot-ui files to $target_dir"
