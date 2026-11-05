# 按需下载字体（MiSans）

阅读器的「小米 MiSans」字体**不打进 APK**（单字重 7.4–7.7MB），按「字体 + 字重」
在首次选用时从本目录的 raw 地址下载到应用私有目录（`filesDir/fonts/`），之后离线可用。
切换字重只下载缺失的那个文件，已下载的保留。

## 文件与字重

| 文件 | 字重（usWeightClass） |
|---|---|
| `MiSans-Thin.ttf` | 100 |
| `MiSans-ExtraLight.ttf` | 200 |
| `MiSans-Light.ttf` | 300 |
| `MiSans-Regular.ttf` | 400 |
| `MiSans-Medium.ttf` | 500 |
| `MiSans-Semibold.ttf` | 600 |
| `MiSans-Bold.ttf` | 700 |
| `MiSans-Heavy.ttf` | 900 |

官方包里的 `Normal`(400) 与 `Demibold`(600) 与 `Regular` / `Semibold` 字重相同，
滑块只能按数值选，故未收录（避免死文件）。

设置里的字重（100..900）按最近距离映射到上表，距离相同取更重的一侧（800 → Heavy）。

## 许可

MiSans 面向全社会免费商用；**允许嵌入**，须在软件中注明使用了 MiSans；
不得改变字形外观。说明见 `MiSans-NOTICE.txt`。

所有文件都是从小米官方包中原样取出的，未做任何修改（未子集化、未改名）。

下载地址（与 `AndroidFontStore.FONT_RAW_BASE` 对应）：

```
https://raw.githubusercontent.com/2436238575/reader-rust-app/master/fonts/<文件名>
```
