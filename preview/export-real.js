const fs = require("fs");
const path = require("path");

const dir = path.join("preview", "out", "year");

function triggerOf(build) {
  const causes = [];
  for (const action of build.actions || []) {
    if (action && action.causes) {
      causes.push(...action.causes);
    }
  }
  const kind = (cause) => cause._class || "";
  if (causes.some((cause) => kind(cause).includes("UserIdCause"))) {
    return "manual";
  }
  if (causes.some((cause) => kind(cause).includes("SCMTrigger"))) {
    return "scm";
  }
  if (causes.some((cause) => kind(cause).includes("TimerTrigger"))) {
    return "timer";
  }
  if (causes.some((cause) => kind(cause).includes("UpstreamCause"))) {
    return "upstream";
  }
  return "other";
}

function actorOf(build) {
  for (const action of build.actions || []) {
    for (const cause of (action && action.causes) || []) {
      if ((cause._class || "").includes("UserIdCause") && cause.userId) {
        return String(cause.userId);
      }
    }
  }
  return "";
}

function clean(value) {
  return String(value == null ? "" : value).replace(/[\t\r\n]/g, " ");
}

const byJob = new Map();
for (const file of fs.readdirSync(dir)) {
  if (!file.endsWith(".json")) {
    continue;
  }
  const data = JSON.parse(fs.readFileSync(path.join(dir, file), "utf8"));
  const builds = data.allBuilds || data.builds || [];
  const name = data.name;
  if (!name) {
    continue;
  }
  const prev = byJob.get(name);
  if (!prev || builds.length > prev.length) {
    byJob.set(name, builds);
  }
}

const lines = [];
let total = 0;
for (const [job, builds] of byJob) {
  total += builds.length;
  for (const build of builds) {
    const running = build.building === true || build.result == null;
    const result = running ? "RUNNING" : build.result;
    lines.push(
      [
        "B",
        job,
        build.number,
        clean(build.displayName),
        build.timestamp,
        running ? 0 : build.duration || 0,
        0,
        result,
        triggerOf(build),
        clean(actorOf(build)),
      ].join("\t")
    );
  }
}

const out = path.join("preview", "out", "samples.tsv");
fs.writeFileSync(out, lines.join("\n") + "\n", "utf8");
console.log("jobs", byJob.size, "builds", total, "lines", lines.length);
