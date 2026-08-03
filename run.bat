@echo off
cd /d D:\M78netdisk\doc-generator

echo ============================================
echo  M78 文档生成服务
echo  端口: 8001
echo ============================================

echo.
echo [1/2] 安装依赖...
pip install -r requirements.txt -q

echo.
echo [2/2] 启动服务...
uvicorn main:app --host 0.0.0.0 --port 8001 --reload

pause
