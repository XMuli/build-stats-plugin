# Build Stats

[中文说明](README.zh-CN.md)

Build Stats turns the builds you can see into one page: counts, duration, queue time, success rate, who triggered them, and pipeline stage time. It also shows this computer's CPU, memory, disk, GPU, and network.

Requires Jenkins 2.555.3 or newer, and Java 21 on the controller.

## How to open it

After install, the chart icon at the top right of Jenkins opens the plugin. On the Jenkins home page, the status card sits at the bottom right. Drag it, dock it to the right edge, or close it. The picture opens Build Stats. The text under the picture opens that build.

![Where the plugin is opened from](docs/images/entry.svg)

The page address is `/build-stats`.

## What the page shows

Numbers are computed from builds visible to the signed-in user.

- Overview: heatmap, cumulative machine time, success and failure, build count, time of day, success rate, result, machine time, trigger, and trigger account.
- One page per pipeline. Stage duration is included when Pipeline: Stage View data exists.
- A status plate for the newest build in the current range.
- This computer: CPU, memory, disk, GPU, network, and disk throughput.

The page follows the Jenkins language. On About, Language can be set to 中文 or English. That choice stays in this browser and overrides Jenkins until you change it again.

## About

The author and GitHub link are fixed. A replacement logo stays in this browser only. **Restore default** clears it.

| Field | Default |
| --- | --- |
| Author | XMuli |
| GitHub | https://github.com/XMuli/build-stats-plugin |
| Logo | `src/main/webapp/images/logo.jpg` |

Replace `logo.jpg` to change the image every new browser sees.

## Build

JDK 21.

```
mvn package
```

The package is `target/build-stats.hpi`. Until the plugin is hosted by the Jenkins project, install that file from **Manage Jenkins → Plugins → Advanced settings → Deploy Plugin**.

## Publish to the Jenkins update center

Listing on [plugins.jenkins.io](https://plugins.jenkins.io) happens only after the Jenkins hosting team forks this repository into `jenkinsci`. Releases then go out through `.github/workflows/cd.yaml` when ci.jenkins.io reports a passing build. Version numbers look like `123.vabcdef`, not `1.0.0`.

The steps are in the [plugin hosting guide](https://www.jenkins.io/doc/developer/publishing/requesting-hosting/). Open a hosting request in [repository-permissions-updater](https://github.com/jenkins-infra/repository-permissions-updater/issues/new/choose) for `build-stats-plugin`.

A tag on this repository can still attach an `.hpi` to a GitHub Release for a manual install. That release is not the Jenkins update center.
