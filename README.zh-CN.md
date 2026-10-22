# 构建统计

[English](README.md)

构建统计把当前账号能看到的构建收成一页：次数、耗时、排队、成功率、谁触发的，以及流水线阶段耗时。同时显示这台电脑的 CPU、内存、磁盘、显卡和网络。

控制器需要 Jenkins 2.555.3 或更新版本，并使用 Java 21。

## 从哪里进来

安装后，Jenkins 右上角的折线图标进入插件。首页右下角有一张状态牌，可以拖动、收到屏幕右边，或关闭。点图片进入构建统计，点图片下面的文字打开这一次构建。

![入口示意](docs/images/entry.svg)

页面地址是 `/build-stats`。

## 页面上有什么

数字按当前账号能看到的构建现算。

- 总览：热力图、累计机器时间、成功和失败、构建次数、时间段、成功率、结果、机器时间、触发方式、触发账号。
- 每条流水线一页。有 Pipeline Stage View 数据时包含阶段耗时。
- 当前范围内最新一条构建的状态牌。
- 本机：CPU、内存、磁盘、显卡、网速、磁盘读写。

页面语言默认跟随 Jenkins。在「关于」里可以把语言改成中文或 English。这个选择只留在这台浏览器里，并盖过 Jenkins 的语言，直到再次修改。

## 关于

作者和 GitHub 仓库地址是固定的。Logo 可以换成自己的图片，只留在这台浏览器里。点「恢复默认」会回到自带的图。

| 项 | 默认 |
| --- | --- |
| 作者 | XMuli |
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
