# LaTeX 交付文档

本目录包含根据实际源码、数据库迁移和测试记录生成的三套中文文档。已编译 PDF 位于 `output/`，系统运行截图位于 `figures/screenshots/`。

## 编译

推荐使用 Tectonic 0.17 或带中文支持的 XeLaTeX。在三个文档子目录中分别执行：

```powershell
tectonic main.tex
```

文档通过 `../common-settings.tex` 共享版式，并相对引用截图。2026-10-14 的交付 PDF 使用官方便携 Tectonic 0.17 生成；宿主系统没有安装 `xelatex`/`latexmk`。

## 内容边界

- 所有页面截图来自实际运行系统，但数据均为模拟或合成数据。
- Yente 与 Fineract 仅完成适配器、契约和失败处理验证，未宣称真实服务联调成功。
- R004–R008、完整 KYC 工作流、用户后台和正式监管报送等未实现能力在文档中明确列出。
