# 构建统计

<p align="center">
  <a href="https://build-stats-plugin.xmuli.tech/"><img alt="官网" src="https://img.shields.io/badge/website-build--stats--plugin.xmuli.tech-3ca2ea"></a>
  <a href="https://github.com/XMuli/build-stats-plugin"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-XMuli%2Fbuild--stats--plugin-173044"></a>
  <a href="https://www.jenkins.io/"><img alt="Jenkins 2.555.3 或更新版本" src="https://img.shields.io/badge/Jenkins-2.555.3%2B-D24939?logo=jenkins&logoColor=white"></a>
  <a href="https://adoptium.net/"><img alt="Java 21" src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-4d6270"></a>
</p>

<p align="center">
  <a href="https://build-stats-plugin.xmuli.tech/"><strong>官网</strong></a>
  &nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="https://github.com/XMuli/build-stats-plugin"><strong>GitHub</strong></a>
  &nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="README.md"><strong>English</strong></a>
</p>

构建统计把当前账号能看到的构建收成一页：次数、耗时、排队、成功率、谁触发的，以及流水线阶段耗时。同时显示这台电脑的 CPU、内存、磁盘、显卡和网络。

控制器需要 Jenkins 2.555.3 或更新版本，并使用 Java 21。安装后打开 `/build-stats`。

## 页面示例

### 总览

<p align="center">
  <a href="https://build-stats-plugin.xmuli.tech/#overview">
    <img src="docs/images/overview.png" alt="构建统计总览：热力图、趋势和右侧状态牌" width="860">
  </a>
</p>

热力图、累计机器时间、成功和失败、构建次数、时间段、成功率、结果、机器时间、触发方式、触发账号。数字按当前账号能看到的构建现算。[在官网查看这一节](https://build-stats-plugin.xmuli.tech/#overview)。

### 一条流水线

<p align="center">
  <a href="https://build-stats-plugin.xmuli.tech/#pipeline">
    <img src="docs/images/pipeline.png" alt="一条流水线的耗时、次数和成功率" width="860">
  </a>
</p>

每条流水线单独一页。有 Pipeline Stage View 数据时包含阶段耗时。[在官网查看这一节](https://build-stats-plugin.xmuli.tech/#pipeline)。

### 这台电脑

<p align="center">
  <a href="https://build-stats-plugin.xmuli.tech/#machine">
    <img src="docs/images/host.png" alt="本机的 CPU、内存、磁盘、显卡和网络" width="860">
  </a>
</p>

运行 Jenkins 的这台机器：CPU、内存、磁盘、显卡、网速，以及磁盘读写。[在官网查看这一节](https://build-stats-plugin.xmuli.tech/#machine)。

### 状态牌

总览和每条流水线都挂着这块牌子，跟着当前范围内最新一次构建变。

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/images/hybrid-zaiganle.jpg" alt="在干了" width="180"><br><strong>在干了</strong><br>正在构建</td>
    <td align="center" width="25%"><img src="docs/images/hybrid-ganwanle.jpg" alt="干完了" width="180"><br><strong>干完了</strong><br>成功</td>
    <td align="center" width="25%"><img src="docs/images/hybrid-ganbuwan.jpg" alt="干不完" width="180"><br><strong>干不完</strong><br>失败或不稳定</td>
    <td align="center" width="25%"><img src="docs/images/hybrid-buganle.jpg" alt="不干了" width="180"><br><strong>不干了</strong><br>中止或未构建</td>
  </tr>
</table>

[在官网查看状态牌](https://build-stats-plugin.xmuli.tech/#plates)。

## 从哪里进来

安装后，Jenkins 右上角的折线图标进入插件。首页右下角有一张状态牌，可以拖动、收到屏幕右边，或关闭。点图片进入构建统计，点图片下面的文字打开这一次构建。

<p align="center">
  <img src="docs/images/entry.svg" alt="从 Jenkins 右上角图标和首页状态牌进入构建统计" width="860">
</p>

<p align="center">
  <img src="docs/images/home.png" alt="首页右下角的状态牌" width="280">
</p>

页面地址是 `/build-stats`。

页面语言默认跟随 Jenkins。在「关于」里可以把语言改成中文或 English。这个选择只留在这台浏览器里，并盖过 Jenkins 的语言，直到再次修改。

## 关于

作者和 GitHub 仓库地址是固定的。Logo 可以换成自己的图片，只留在这台浏览器里。点「恢复默认」会回到自带的图。

| 项 | 默认 |
| --- | --- |
| 作者 | XMuli |
| 官网 | https://build-stats-plugin.xmuli.tech/ |
| GitHub | https://github.com/XMuli/build-stats-plugin |
| Logo | `src/main/webapp/images/logo.jpg` |

要让所有新打开的浏览器都看到新 Logo，换掉 `logo.jpg`。

## 构建

JDK 21。

```
mvn package
```

产物是 `target/build-stats.hpi`。在插件被 Jenkins 项目托管之前，从 **系统管理 → 插件管理 → 高级设置 → 部署插件** 安装这个文件。

## 上架到 Jenkins 更新中心

出现在 [plugins.jenkins.io](https://plugins.jenkins.io) 之前，要先由 Jenkins 托管团队把这个仓库 fork 到 `jenkinsci`。之后由 `.github/workflows/cd.yaml` 在 ci.jenkins.io 构建通过时发布。版本号形如 `123.vabcdef`，不是 `1.0.0`。

步骤见 [插件托管说明](https://www.jenkins.io/doc/developer/publishing/requesting-hosting/)。在 [repository-permissions-updater](https://github.com/jenkins-infra/repository-permissions-updater/issues/new/choose) 为 `build-stats-plugin` 提交 Hosting request。

在此之前，给这个仓库打 tag 仍会在 GitHub Release 里附上 `.hpi`，方便手动安装。那不是 Jenkins 更新中心。
