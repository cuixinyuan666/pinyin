#!/usr/bin/env bash
# Cloud Agent 环境安装脚本：为「拼音识字乐园」离线 APK 构建准备 Android SDK。
#
# 该项目不使用 Gradle / Android Studio，仅依赖 Android SDK 命令行工具
# (aapt2 / d8 / zipalign / apksigner) 与 JDK 直接离线构建 APK。
# 基础镜像已自带 JDK，本脚本只负责安装 Android SDK。
#
# 特性：幂等（已安装则跳过下载）、非交互、可重复运行。
set -euo pipefail

ANDROID_SDK="${ANDROID_SDK:-/opt/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

# 项目实际需要的三个组件（与 build_apk.sh 中的版本保持一致）
BUILD_TOOLS="build-tools;35.0.0"
PLATFORM="platforms;android-34"
PLATFORM_TOOLS="platform-tools"

echo "==> Android SDK 目标目录: ${ANDROID_SDK}"

# 创建 SDK 目录（/opt 需要 root，用 sudo 后再交回当前用户）
if [ ! -d "${ANDROID_SDK}" ]; then
  if [ -w "$(dirname "${ANDROID_SDK}")" ]; then
    mkdir -p "${ANDROID_SDK}"
  else
    sudo mkdir -p "${ANDROID_SDK}"
    sudo chown -R "$(id -u):$(id -g)" "${ANDROID_SDK}"
  fi
fi

SDKMANAGER="${ANDROID_SDK}/cmdline-tools/latest/bin/sdkmanager"

# 1) 安装 command line tools（仅当缺失时下载）
if [ ! -x "${SDKMANAGER}" ]; then
  echo "==> 下载并安装 Android command line tools"
  TMP_ZIP="$(mktemp -d)/cmdtools.zip"
  curl -fsSL -o "${TMP_ZIP}" "${CMDLINE_TOOLS_URL}"
  TMP_EXTRACT="$(mktemp -d)"
  unzip -q "${TMP_ZIP}" -d "${TMP_EXTRACT}"
  mkdir -p "${ANDROID_SDK}/cmdline-tools"
  rm -rf "${ANDROID_SDK}/cmdline-tools/latest"
  mv "${TMP_EXTRACT}/cmdline-tools" "${ANDROID_SDK}/cmdline-tools/latest"
  rm -rf "${TMP_ZIP}" "${TMP_EXTRACT}"
else
  echo "==> command line tools 已存在，跳过下载"
fi

# 2) 接受许可协议（幂等）
yes | "${SDKMANAGER}" --licenses >/dev/null 2>&1 || true

# 3) 安装构建所需组件（sdkmanager 对已安装组件是幂等的）
echo "==> 安装 ${PLATFORM_TOOLS} / ${PLATFORM} / ${BUILD_TOOLS}"
"${SDKMANAGER}" "${PLATFORM_TOOLS}" "${PLATFORM}" "${BUILD_TOOLS}"

# 4) 校验关键文件是否就位
AJ="${ANDROID_SDK}/platforms/android-34/android.jar"
AAPT2="${ANDROID_SDK}/build-tools/35.0.0/aapt2"
if [ ! -f "${AJ}" ] || [ ! -x "${AAPT2}" ]; then
  echo "ERROR: Android SDK 组件缺失，安装失败。" >&2
  exit 1
fi

echo "==> Android SDK 就绪。构建命令: ANDROID_SDK=${ANDROID_SDK} bash build_apk.sh"
