# M78 文档生成服务

独立 Python 微服务，端口 8001。将 AI 生成的 Markdown 内容转换为多种文档格式。

## 技术栈

| 组件 | 技术 |
|------|------|
| 框架 | FastAPI + Uvicorn |
| Markdown 解析 | mistune 3（AST 解析） |
| Word 生成 | python-docx（标题/表格/列表/代码块样式映射） |
| Excel 生成 | openpyxl（多表格 → 多 Sheet，自动推断数字类型） |
| 模板渲染 | Jinja2 |

## API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/generate` | POST | 生成文档（body: `{ "content": "...", "format": "docx\|xlsx\|md\|html\|json\|csv", "title": "文档标题" }`），返回文件流 |
| `/health` | GET | 健康检查 |

## 支持的格式

| 格式 | 说明 |
|------|------|
| `md` | Markdown 原文 |
| `txt` | 纯文本（去除 Markdown 标记） |
| `docx` | Word 文档（含样式：标题/粗体/斜体/代码块/表格） |
| `xlsx` | Excel 工作簿（多表格 → 多 Sheet） |
| `html` | 已有 HTML 原样返回 |
| `json` | 已有 JSON 原样返回 |
| `csv` | CSV 格式返回 |

## 快速启动

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

或 Windows 下直接运行 `run.bat`。

## 协议

MIT License
