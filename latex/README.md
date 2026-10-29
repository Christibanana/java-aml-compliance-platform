# LaTeX 交付文档

本目录包含根据实际源码、数据库迁移和测试记录生成的三套中文文档。已编译 PDF 位于 `output/`，系统运行截图位于 `figures/screenshots/`。

## 编译

推荐使用 MiKTeX 26.5 提供的 XeLaTeX。在三个文档子目录中分别执行两次，以更新目录、页码和交叉引用：

```powershell
xelatex -interaction=nonstopmode -halt-on-error main.tex
xelatex -interaction=nonstopmode -halt-on-error main.tex
```

文档通过 `../common-settings.tex` 共享 A4 版式，并相对引用截图。2026-10-15 的交付 PDF 使用 MiKTeX XeLaTeX 生成，中文字体已嵌入；VS Code 已安装 LaTeX Workshop，并配置为使用同一编译器。当前环境未安装 Perl，因此不采用依赖 Perl 的 `latexmk` 流程。

## 内容边界

- 所有页面截图来自实际运行系统，但数据均为模拟或合成数据。
- Yente 与 Fineract 仅完成适配器、契约和失败处理验证，未宣称真实服务联调成功。
- R004–R008、用户后台和正式监管报送等未实现能力在文档中明确列出；KYC 小流程已实现，但无自动提醒或强制双人分离。
