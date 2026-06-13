#!/bin/bash
# 项目启动脚本 - 自动使用 JDK 21

# 设置 JDK 21
export JAVA_HOME=/Users/heidashuai/Library/Java/JavaVirtualMachines/temurin-21.0.7/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

echo "🚀 启动 AetherfallChronicles 后端服务..."
echo "📍 JAVA_HOME: $JAVA_HOME"
echo ""

# 启动 Spring Boot (从项目根目录)
mvn spring-boot:run -pl mythic-realm-starter
