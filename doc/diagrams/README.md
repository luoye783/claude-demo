# Diagrams 索引

> 所有架构图存放在本目录。
> 当前阶段使用 **Mermaid** 源文件（`.mmd`）+ 可选的 SVG 渲染产物。

---

## 一、文件清单

| 文件 | 类型 | 用途 |
|------|------|------|
| `global-architecture.mmd` | Mermaid 源 | 全局五大子系统架构图 |
| `call-chain-happy-path.mmd` | Mermaid 源 | NL2SQL Agent 时序图（Happy Path） |
| `module-dependency.mmd` | Mermaid 源 | 模块依赖方向图 |
| `memory-compression-flow.mmd` | Mermaid 源 | 压缩触发流程图 |
| `*.svg` | 渲染产物 | Mermaid → SVG 转换结果（可选） |

> Mermaid 源是文档源；SVG 是渲染结果（用于在不支持 Mermaid 的渲染器中查看）。

---

## 二、渲染方式

### 方式 1：GitHub / GitLab / VSCode Markdown Preview
直接查看 `.md` 文件,Mermaid 代码块会自动渲染。无需额外步骤。

### 方式 2：Mermaid CLI（生成 SVG/PNG）

```bash
# 安装
npm install -g @mermaid-js/mermaid-cli

# 渲染单个文件
mmdc -i global-architecture.mmd -o global-architecture.svg

# 批量渲染
for f in *.mmd; do mmdc -i "$f" -o "${f%.mmd}.svg"; done
```

### 方式 3：在线 Mermaid Live Editor
打开 https://mermaid.live/，把 `.mmd` 内容粘贴进去即可预览。

---

## 三、新增图表流程

1. 在本目录创建 `your-diagram.mmd`。
2. 在 `README.md`（项目根 `doc/`）或相关 `.md` 中通过相对路径引用。
3. 如需 SVG 产物，用 mmdc 渲染后提交到 git。

---

## 四、当前图表说明

### global-architecture.mmd
五个子系统 + 主链路 + Memory/Trace 外圈。配套文字说明见 [`../README.md`](../README.md) 和 [`../architecture/boundaries.md`](../architecture/boundaries.md)。

### call-chain-happy-path.mmd
HTTP → Agent → Session/Memory/MCP/LLM 完整时序。配套文字说明见 [`../architecture/call-chain.md`](../architecture/call-chain.md)。

### module-dependency.mmd
包与包的依赖方向。配套文字说明见 [`../architecture/modules.md`](../architecture/modules.md) §二。

### memory-compression-flow.mmd
CompressionPolicy 触发 → MemoryCompressor 调 LLM → 写回新 SummaryMemory 的流程。配套文字说明见 [`../architecture/modules.md`](../architecture/modules.md) §四。
