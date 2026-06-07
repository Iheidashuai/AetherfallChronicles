#!/bin/bash
# 项目专用 JDK 21 环境脚本
# 使用方法: source setjdk21.sh

# 保存原始 JAVA_HOME
export ORIGINAL_JAVA_HOME=$JAVA_HOME
export ORIGINAL_PATH=$PATH

# 设置 JDK 21 路径
export JAVA_HOME=/Users/heidashuai/Library/Java/JavaVirtualMachines/temurin-21.0.7/Contents/Home

# 更新 PATH，将 JDK 21 的 bin 目录放在最前面
export PATH="$JAVA_HOME/bin:$PATH"

echo "✅ 已切换到 JDK 21 (仅当前终端会话)"
echo "📍 JAVA_HOME: $JAVA_HOME"
java -version

echo ""
echo "💡 提示:"
echo "  - 此设置仅影响当前终端会话"
echo "  - 关闭终端后自动恢复系统 JDK 11"
echo "  - 恢复命令: source restore_jdk.sh"
