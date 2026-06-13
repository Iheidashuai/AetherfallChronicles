#!/bin/bash
# 恢复原始 JDK 环境

if [ -n "$ORIGINAL_JAVA_HOME" ]; then
    export JAVA_HOME=$ORIGINAL_JAVA_HOME
    export PATH=$ORIGINAL_PATH
    echo "✅ 已恢复原始 JDK 环境"
    java -version
else
    echo "⚠️  未找到原始 JAVA_HOME，请重新打开终端"
fi
