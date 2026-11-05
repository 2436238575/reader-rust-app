# 按需下载字体

阅读器的「鸿蒙 / 小米」两个字体族**不打进 APK**（单字重 7.4–8.0MB），按「字体 + 字重」
在首次选用时从本目录的 raw 地址下载到应用私有目录（`filesDir/fonts/`），之后离线可用。
切换字重只下载缺失的那个文件，已下载的保留。

## 文件与字重

| 文件 | 字重（usWeightClass） |
|---|---|
| `HarmonyOS_Sans_SC_Thin.ttf` | 250 |
| `HarmonyOS_Sans_SC_Light.ttf` | 300 |
| `HarmonyOS_Sans_SC_Regular.ttf` | 400 |
| `HarmonyOS_Sans_SC_Medium.ttf` | 500 |
| `HarmonyOS_Sans_SC_Bold.ttf` | 700 |
| `HarmonyOS_Sans_SC_Black.ttf` | 900 |
| `MiSans-Thin.ttf` | 100 |
| `MiSans-ExtraLight.ttf` | 200 |
| `MiSans-Light.ttf` | 300 |
| `MiSans-Regular.ttf` | 400 |
| `MiSans-Medium.ttf` | 500 |
| `MiSans-Semibold.ttf` | 600 |
| `MiSans-Bold.ttf` | 700 |
| `MiSans-Heavy.ttf` | 900 |

MiSans 官方包里的 `Normal`(400) 与 `Demibold`(600) 与 `Regular` / `Semibold` 字重相同，
滑块只能按数值选，故未收录（避免死文件）。

设置里的字重（100..900）按最近距离映射到上表，距离相同取更重的一侧：
例如 600 在 Harmony 上落到 Bold，800 落到 Black。

## 许可

- **HarmonyOS Sans**：允许随软件分发**未修改**的副本；**禁止修改**（不能子集化/改名）；
  须显著标注；不得以独立形式再分发字体本身。原文见 `HarmonyOS-Sans-LICENSE.txt`。
- **MiSans**：免费商用；**允许嵌入**，须注明使用了 MiSans；不得改变字形外观。
  说明见 `MiSans-NOTICE.txt`。

所有文件都是从厂商官方包中原样取出的，未做任何修改。

下载地址（与 `AndroidFontStore.FONT_RAW_BASE` 对应）：

```
https://raw.githubusercontent.com/2436238575/reader-rust-app/master/fonts/<文件名>
```

> ⚠️ HarmonyOS 许可第 3 条明确禁止「以独立形式（stand-alone）再分发字体文件」。
> 本仓库是公开仓库，把 TTF 放在这里属于该条款覆盖的行为。若需要严格合规，
> 应改为让 App 直接从华为官方下载包获取，或将这些文件移出公开仓库。
