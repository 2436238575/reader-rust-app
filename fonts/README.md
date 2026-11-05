# 按需下载字体

阅读器的「鸿蒙 / 小米」两个字体**不打进 APK**（单字重 7.5–7.9MB），首次选用时由 App 从本目录的
raw 地址下载到应用私有目录（`filesDir/fonts/`），之后完全离线可用。

| 文件 | 字体 | 许可要点 |
|---|---|---|
| `HarmonyOS_Sans_SC_Regular.ttf` | HarmonyOS Sans SC Regular | 允许随软件分发**未修改**的副本；**禁止修改**（不能子集化）；须显著标注；不得以独立形式再分发字体本身 |
| `MiSans-Regular.ttf` | MiSans Regular | 免费商用；允许嵌入，须注明使用了 MiSans；不得改变字形外观 |

两个文件都是从厂商官方包中原样取出的 **Regular 字重**，未子集化、未改名、未做任何修改。
授权原文/说明见同目录的 `HarmonyOS-Sans-LICENSE.txt` 与 `MiSans-NOTICE.txt`。

下载地址（与 `AndroidFontStore` 中的 `FONT_RAW_BASE` 对应）：

```
https://raw.githubusercontent.com/2436238575/reader-rust-app/master/fonts/HarmonyOS_Sans_SC_Regular.ttf
https://raw.githubusercontent.com/2436238575/reader-rust-app/master/fonts/MiSans-Regular.ttf
```

> ⚠️ HarmonyOS 许可第 3 条明确禁止「以独立形式（stand-alone）再分发字体文件」。
> 本仓库是公开仓库，把该 TTF 放在这里属于该条款覆盖的行为。若需要严格合规，
> 应改为让 App 直接从华为官方下载包获取，或将该文件移出公开仓库。
