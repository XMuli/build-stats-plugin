/*
 * Behaviour for the Build Stats pages and for the status card the page decorator adds elsewhere.
 *
 * The pages are rendered on the server, so this file never produces visible text and never
 * translates anything: every string a reader sees comes from the resource bundle for the request's
 * locale and is already in the first HTML response. What is left here is the part a server cannot
 * do: browser-local preferences, navigating from a <select>, the chart hover preview, and the
 * live sampler on the "this computer" page.
 *
 * Loaded as one external file with no inline handlers, no eval and no new Function, so a strict
 * Content-Security-Policy accepts the page as it is.
 */
(function () {
  "use strict";

  // The page and the decorator's footer can both pull this file into one document. Initialise
  // once so no listener is attached twice.
  if (window.__bvStats) {
    return;
  }
  window.__bvStats = true;

  var root = document.documentElement;

  /* ------------------------------------------------------------------ browser storage
   *
   * localStorage throws outright in some privacy modes rather than returning null, so every
   * access is wrapped. A browser that refuses to remember a preference should still show a
   * working page with that preference at its default.
   */

  function readStore(key) {
    try {
      return localStorage.getItem(key);
    } catch (e) {
      return null;
    }
  }

  function writeStore(key, value) {
    try {
      localStorage.setItem(key, value);
      return true;
    } catch (e) {
      return false;
    }
  }

  function dropStore(key) {
    try {
      localStorage.removeItem(key);
    } catch (e) {}
  }

  /* ------------------------------------------------------------------ small DOM helpers */

  var SVG_NS = "http://www.w3.org/2000/svg";

  function all(selector, within) {
    return Array.prototype.slice.call((within || document).querySelectorAll(selector));
  }

  function clear(node) {
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  /** An SVG element with attributes. Used instead of innerHTML so no value is ever parsed as markup. */
  function svg(name, attrs) {
    var node = document.createElementNS(SVG_NS, name);
    Object.keys(attrs || {}).forEach(function (key) {
      node.setAttribute(key, String(attrs[key]));
    });
    return node;
  }

  function el(name, className, text) {
    var node = document.createElement(name);
    if (className) {
      node.className = className;
    }
    if (text != null) {
      node.textContent = text;
    }
    return node;
  }

  /* ------------------------------------------------------------------ 1. preference toggles
   *
   * Three browser-local preferences, each a class on <html> that the stylesheet acts on. Doing it
   * with a class rather than by touching the elements keeps the rule in the stylesheet, where the
   * rest of the layout lives, and means the preference also reaches the decorator's card on pages
   * this script otherwise has nothing to do with.
   *
   * The storage keys predate the "status" wording and are shared with the home-page card, so they
   * keep their original names: renaming them would silently reset everyone's preference.
   */

  var PREFS = {
    summary: { key: "bv-summary-show", on: "1", off: "0", fallback: false },
    status: { key: "bv-sign-show", on: "1", off: "0", fallback: true }
  };
  var WHERE_KEY = "bv-sign-where";

  function prefOn(name) {
    var spec = PREFS[name];
    var saved = readStore(spec.key);
    return saved === null ? spec.fallback : saved === spec.on;
  }

  function homeOnly() {
    return readStore(WHERE_KEY) === "home";
  }

  /*
   * Called while the document is still parsing, so the stored choice is in place before the
   * browser paints the affected elements and nothing visibly flips afterwards.
   */
  function applyPrefClasses() {
    root.classList.toggle("bv-summary-on", prefOn("summary"));
    root.classList.toggle("bv-status-off", !prefOn("status"));
    root.classList.toggle("bv-status-home", homeOnly());
  }

  /** Put the controls themselves in step with storage, once they exist. */
  function reflectPrefControls() {
    all("input.bv-pref[data-pref]").forEach(function (box) {
      var name = box.getAttribute("data-pref");
      if (PREFS[name]) {
        box.checked = prefOn(name);
      }
    });
    all('select.bv-pref-select[data-pref="statusWhere"]').forEach(function (select) {
      select.value = homeOnly() ? "home" : "all";
      // With the plate switched off this select governs nothing, so it is not offered.
      select.disabled = !prefOn("status");
    });
    applyHomeCard();
  }

  function choosePref(name, on) {
    var spec = PREFS[name];
    if (!spec) {
      return;
    }
    writeStore(spec.key, on ? spec.on : spec.off);
    applyPrefClasses();
    reflectPrefControls();
  }

  function chooseWhere(value) {
    writeStore(WHERE_KEY, value === "home" ? "home" : "all");
    applyPrefClasses();
    reflectPrefControls();
  }

  /* ------------------------------------------------------------------ 2. select as navigation
   *
   * Each option of a .bv-go select carries its own URL, so the server renders a working control
   * and this only saves a click. "change" rather than "input" is what makes it safe for the
   * keyboard: on an unopened select, arrow keys move through the options and every browser fires
   * input for each step, while change waits for the choice to be committed (Enter, Tab, blur, or
   * picking from the open list). Listening to input would navigate away mid-selection.
   */

  function navigateFrom(select) {
    var url = select.value;
    if (url) {
      window.location.assign(url);
    }
  }

  /* ------------------------------------------------------------------ 3. machine sampling
   *
   * The "this computer" page renders empty cards and this fills them from two endpoints that hang
   * off the page's own URL. Nothing starts unless those cards are in the document, so the overview
   * and About pages never open a request, and the timer is dropped whenever the tab is hidden —
   * a background tab that keeps a controller sampling its own CPU every second is pure waste.
   */

  var SPARK_W = 320;
  var SPARK_H = 120; // Matches the viewBox computer.jelly renders on every .bv-spark.
  var CORE_W = 80;
  var CORE_H = 24; // Matches the height the stylesheet gives .bv-core svg.
  var SAMPLES = 60;

  // One colour per series so neighbouring cards stay distinguishable. These are literals in this
  // file, never values from a sample, so they are safe to put in a stroke attribute.
  var SERIES = {
    cpu: { colour: "#3479b0", rate: false },
    mem: { colour: "#3c74a3", rate: false },
    disk: { colour: "#f08048", rate: false },
    down: { colour: "#3479b0", rate: true },
    up: { colour: "#3c74a3", rate: true },
    read: { colour: "#f08048", rate: true },
    write: { colour: "#50a040", rate: true }
  };
  var GPU_COLOUR = "#50a040";

  var history = {};
  Object.keys(SERIES).forEach(function (key) {
    history[key] = [];
  });
  var coreHistory = [];
  var gpuHistory = [];
  var pace = 1;
  var timer = 0;
  var sampling = false;
  var activityDefaults = null;

  function record(series, value) {
    if (value == null || typeof value !== "number" || isNaN(value)) {
      return;
    }
    series.push(value);
    if (series.length > SAMPLES) {
      series.shift();
    }
  }

  function percentText(value) {
    return value == null || isNaN(value) ? "--" : Math.round(value) + "%";
  }

  /** "GB" and "B/s" are unit symbols, not words, so composing them here needs no translation. */
  function gigabytes(bytes) {
    if (bytes == null || bytes < 0) {
      return "";
    }
    var value = bytes / 1073741824;
    return (value >= 10 ? value.toFixed(1) : value.toFixed(2)) + " GB";
  }

  function rateText(bytesPerSecond) {
    if (bytesPerSecond == null || isNaN(bytesPerSecond)) {
      return "--";
    }
    var units = ["B/s", "KB/s", "MB/s", "GB/s"];
    var value = bytesPerSecond;
    var step = 0;
    while (value >= 1024 && step < units.length - 1) {
      value /= 1024;
      step++;
    }
    var shown = step === 0 ? value.toFixed(0) : value >= 10 ? value.toFixed(1) : value.toFixed(2);
    return shown + " " + units[step];
  }

  /** The polyline through one history, as an SVG path built from numbers this file computed. */
  function trace(values, top, width, height, pad) {
    var d = "";
    for (var i = 0; i < values.length; i++) {
      var x = values.length < 2 ? 0 : (i / (values.length - 1)) * width;
      var raw = Math.max(0, values[i]);
      var y = height - (raw / top) * (height - pad * 2) - pad;
      d += (i ? "L" : "M") + x.toFixed(1) + " " + y.toFixed(1);
    }
    return d;
  }

  function ceiling(values, floor) {
    var top = floor;
    for (var i = 0; i < values.length; i++) {
      if (values[i] > top) {
        top = values[i];
      }
    }
    return top;
  }

  /*
   * A sparkline: four gridlines, a filled area and the line itself. Percentage series are drawn
   * against a fixed 0-100 so the shape means the same thing from one refresh to the next; byte
   * rates have no natural ceiling, so those scale to the tallest sample held.
   */
  function drawSpark(target, values, colour, rate) {
    if (!target) {
      return;
    }
    clear(target);
    for (var line = 0; line <= 4; line++) {
      var y = (SPARK_H / 4) * line;
      target.appendChild(svg("line", {
        x1: 0,
        y1: y,
        x2: SPARK_W,
        y2: y,
        stroke: "var(--panel-border-color, #e4e9ee)"
      }));
    }
    if (!values.length) {
      return;
    }
    var top = rate ? ceiling(values, 1) : 100;
    var d = trace(values, top, SPARK_W, SPARK_H, 3);
    var close = (values.length < 2 ? 0 : SPARK_W) + " " + SPARK_H;
    target.appendChild(svg("path", {
      d: d + " L" + close + " L0 " + SPARK_H + " Z",
      fill: colour,
      opacity: "0.16"
    }));
    target.appendChild(svg("path", { d: d, fill: "none", stroke: colour, "stroke-width": "1.6" }));
  }

  /** One tiny chart per logical processor, rebuilt only when the core count itself changes. */
  function drawCores(values) {
    if (!values || !values.length) {
      return;
    }
    if (coreHistory.length !== values.length) {
      coreHistory = values.map(function () {
        return [];
      });
    }
    values.forEach(function (value, index) {
      record(coreHistory[index], value);
    });
    all(".bv-core-grid").forEach(function (grid) {
      if (grid.childElementCount !== values.length) {
        clear(grid);
        values.forEach(function () {
          var cell = el("div", "bv-core");
          cell.appendChild(el("b"));
          cell.appendChild(svg("svg", {
            viewBox: "0 0 " + CORE_W + " " + CORE_H,
            preserveAspectRatio: "none",
            "aria-hidden": "true"
          }));
          grid.appendChild(cell);
        });
      }
      values.forEach(function (value, index) {
        var cell = grid.children[index];
        if (!cell) {
          return;
        }
        cell.querySelector("b").textContent = percentText(value);
        var plot = cell.querySelector("svg");
        clear(plot);
        var series = coreHistory[index];
        if (!series.length) {
          return;
        }
        var d = trace(series, 100, CORE_W, CORE_H, 2);
        var close = (series.length < 2 ? 0 : CORE_W) + " " + CORE_H;
        plot.appendChild(svg("path", {
          d: d + " L" + close + " L0 " + CORE_H + " Z",
          fill: SERIES.cpu.colour,
          opacity: "0.2"
        }));
        plot.appendChild(svg("path", {
          d: d,
          fill: "none",
          stroke: SERIES.cpu.colour,
          "stroke-width": "1.2"
        }));
      });
    });
  }

  /*
   * GPU cards exist only when the controller reports a GPU, so computer.jelly leaves an empty slot
   * and they are built here, in the same shape as the cards next to them so they inherit the same
   * styling. The device name and its memory line come from the sample and are set as text, never
   * as markup: a driver that reports an odd name must not be able to put nodes in this page.
   */
  function drawGpus(list) {
    var gpus = list || [];
    all(".bv-gpu-slot").forEach(function (slot) {
      var signature = gpus.map(function (gpu) {
        return gpu.name || "";
      }).join("|");
      if (slot.getAttribute("data-bv-gpu-key") !== signature) {
        clear(slot);
        slot.setAttribute("data-bv-gpu-key", signature);
        gpus.forEach(function (gpu) {
          var card = el("article", "jenkins-card bv-live");
          card.appendChild(el("div", "jenkins-card__title", gpu.name || "GPU"));
          var content = el("div", "jenkins-card__content");
          content.appendChild(el("strong", "bv-live__value", "--"));
          content.appendChild(el("p", "bv-live__sub"));
          content.appendChild(svg("svg", {
            class: "bv-spark",
            viewBox: "0 0 " + SPARK_W + " " + SPARK_H,
            preserveAspectRatio: "none",
            "aria-hidden": "true"
          }));
          card.appendChild(content);
          slot.appendChild(card);
        });
      }
    });
    if (!gpus.length) {
      gpuHistory = [];
      return;
    }
    if (gpuHistory.length !== gpus.length) {
      gpuHistory = gpus.map(function () {
        return [];
      });
    }
    gpus.forEach(function (gpu, index) {
      record(gpuHistory[index], gpu.util == null ? 0 : gpu.util);
    });
    all(".bv-gpu-slot").forEach(function (slot) {
      gpus.forEach(function (gpu, index) {
        var card = slot.children[index];
        if (!card) {
          return;
        }
        card.querySelector(".bv-live__value").textContent = percentText(gpu.util);
        card.querySelector(".bv-live__sub").textContent = gpu.memoryText || "";
        drawSpark(card.querySelector(".bv-spark"), gpuHistory[index], GPU_COLOUR, false);
      });
    });
  }

  /** Per-volume usage inside the disk card. Volume names come from the sample, so again: text. */
  function fillDisks(list) {
    all('[data-live="disk"] .bv-disk-list').forEach(function (box) {
      clear(box);
      (list || []).forEach(function (volume) {
        var row = el("div", "bv-disk");
        row.appendChild(el("span", null, volume.name || ""));
        var bar = el("div", "bv-disk-bar");
        var fill = el("i");
        fill.style.width = Math.max(0, Math.min(100, Number(volume.percent) || 0)) + "%";
        bar.appendChild(fill);
        row.appendChild(bar);
        var used = volume.usedText || "";
        var total = volume.totalText || "";
        row.appendChild(el("span", null, total ? used + " / " + total : used));
        box.appendChild(row);
      });
    });
  }

  function joinParts(parts) {
    return parts.filter(function (part) {
      return part;
    }).join(" · ");
  }

  /*
   * The activity table's rows are rendered by the server with a placeholder in each cell. Those
   * placeholders are remembered on the first pass and put back whenever a list is empty, so an
   * empty state stays in the page's own language instead of being replaced by a string from here.
   */
  function activityRows(data) {
    var last = data.lastRun || {};
    return {
      lastBuild: last.ago ? joinParts([last.job, last.number ? "#" + last.number : "", last.ago]) : "",
      uptime: data.uptime || "",
      users: (data.users || []).map(function (user) {
        return joinParts([user.name, user.since]);
      }).join(", "),
      builds: (data.builds || []).map(function (build) {
        return joinParts([build.job, build.number ? "#" + build.number : "", build.user]);
      }).join(", "),
      downloads: (data.downloads || []).map(function (item) {
        return joinParts([item.path, item.user, item.ago]);
      }).join(", ")
    };
  }

  function fillActivity(data) {
    var cells = all("[data-bv-activity] [data-bv-row]");
    if (!activityDefaults) {
      activityDefaults = {};
      cells.forEach(function (cell) {
        activityDefaults[cell.getAttribute("data-bv-row")] = cell.textContent;
      });
    }
    var rows = activityRows(data);
    cells.forEach(function (cell) {
      var key = cell.getAttribute("data-bv-row");
      var text = rows[key];
      cell.textContent = text ? text : activityDefaults[key] || "";
    });
  }

  function setLive(key, value, sub) {
    all('[data-live="' + key + '"]').forEach(function (card) {
      var slot = card.querySelector(".bv-live__value");
      if (slot) {
        slot.textContent = value;
      }
      // The CPU card's sub line carries the processor description instead, so it is left alone
      // unless a caller passes replacement text.
      if (sub != null) {
        setNote(card.querySelector(".bv-live__sub"), sub);
      }
    });
  }

  /*
   * Replacing a subtitle must not take the sparkline with it. An omitted </p> makes the following
   * <svg> a child of the subtitle, and assigning textContent then deletes the chart.
   */
  function setNote(note, text) {
    if (!note) {
      return;
    }
    var spark = note.querySelector(".bv-spark");
    if (spark && note.parentElement) {
      note.parentElement.appendChild(spark);
    }
    note.textContent = text;
  }

  /** Processor make, core counts and clock, which only the sample knows. Units, not words. */
  function setCpuMeta(sample) {
    var parts = [];
    if (sample.cpuName) {
      parts.push(sample.cpuName);
    }
    if (sample.cpuCores) {
      parts.push(sample.cpuCores + " × core");
    }
    if (sample.cpuLogical) {
      parts.push(sample.cpuLogical + " × thread");
    }
    if (sample.cpuMhz) {
      parts.push((sample.cpuMhz / 1000).toFixed(2) + " GHz");
    }
    if (!parts.length) {
      return;
    }
    all("[data-bv-cpu-meta]").forEach(function (note) {
      setNote(note, parts.join(" · "));
    });
  }

  /*
   * Both endpoints hang off this page's own URL, so they inherit the context path, the reverse
   * proxy prefix and the session without any of that being written down here.
   */
  function endpoint(name) {
    var base = window.location.pathname;
    if (base.charAt(base.length - 1) !== "/") {
      base += "/";
    }
    return base + name;
  }

  function fetchJson(url) {
    return fetch(url, { cache: "no-store", credentials: "same-origin" }).then(function (response) {
      if (!response.ok) {
        throw new Error(String(response.status));
      }
      return response.json();
    });
  }

  /*
   * The sampler pill. Its offline wording is what the server already rendered into it, so it is
   * read once and restored rather than written from here. While sampling, it shows the one-second
   * label the server put on data-bv-live-on. This file has no string of its own to put on screen.
   */
  var offlineLabel = null;

  function setSamplerState(live) {
    all("[data-bv-live-status]").forEach(function (pill) {
      if (offlineLabel == null) {
        offlineLabel = pill.textContent;
      }
      var on = pill.getAttribute("data-bv-live-on");
      pill.textContent = live && on ? on : offlineLabel;
    });
  }

  function paintSample(sample) {
    record(history.cpu, sample.cpu);
    record(history.mem, sample.memoryPercent);
    record(history.disk, sample.diskActive);
    record(history.down, sample.netDown);
    record(history.up, sample.netUp);
    record(history.read, sample.diskRead);
    record(history.write, sample.diskWrite);

    setLive("cpu", percentText(sample.cpu), null);
    setCpuMeta(sample);
    drawCores(sample.cores);

    var used = gigabytes(sample.memoryUsed);
    var total = gigabytes(sample.memoryTotal);
    setLive("mem", percentText(sample.memoryPercent), used && total ? used + " / " + total : null);
    setLive("disk", percentText(sample.diskActive), null);
    setLive("down", rateText(sample.netDown), null);
    setLive("up", rateText(sample.netUp), null);
    setLive("read", rateText(sample.diskRead), null);
    setLive("write", rateText(sample.diskWrite), null);

    Object.keys(SERIES).forEach(function (key) {
      var spec = SERIES[key];
      all('[data-live="' + key + '"] .bv-spark').forEach(function (plot) {
        drawSpark(plot, history[key], spec.colour, spec.rate);
      });
    });

    fillDisks(sample.disks);
    drawGpus(sample.gpus);
  }

  function tick() {
    fetchJson(endpoint("machine") + "?pace=" + pace).then(function (sample) {
      if (sample && sample.ok === false) {
        throw new Error("sampler");
      }
      paintSample(sample);
      setSamplerState(true);
      // Activity is a second, cheaper read. A failure there leaves the resource cards alone.
      return fetchJson(endpoint("activity")).then(fillActivity, function () {});
    }).catch(function () {
      setSamplerState(false);
    });
  }

  function stopSampling() {
    if (timer) {
      clearInterval(timer);
      timer = 0;
    }
  }

  /** Sample now, then on the chosen interval. Called again whenever the interval changes. */
  function startSampling() {
    stopSampling();
    if (!sampling || document.visibilityState === "hidden") {
      return;
    }
    tick();
    timer = window.setInterval(tick, pace * 1000);
  }

  /** Total chart, or one chart per logical processor. The pressed button is the one on screen. */
  function setCpuCores(on) {
    all("[data-live='cpu']").forEach(function (card) {
      card.classList.toggle("bv-live--cores", on);
    });
    all("[data-bv-cpu-mode]").forEach(function (button) {
      var cores = button.getAttribute("data-bv-cpu-mode") === "cores";
      button.setAttribute("aria-pressed", String(cores === on));
    });
    all(".bv-core-grid").forEach(function (grid) {
      grid.hidden = !on;
    });
  }

  /* ------------------------------------------------------------------ 4. About page logo
   *
   * A replacement logo is a per-browser decoration: it is kept as a data URL in this browser's
   * storage and never sent to the controller, so one administrator's choice does not become
   * everyone's. The note under the control is server-rendered text; the alternatives it can switch
   * to are read from data attributes on it, and a state whose attribute is missing leaves the note
   * as it stands rather than putting an untranslated string on the page.
   */

  var LOGO_KEY = "bv-about-logo";
  var LOGO_LIMIT = 2 * 1024 * 1024;
  var logoDefaults = null;

  function logoNotes() {
    var note = document.querySelector("[data-bv-logo-note]");
    if (!note) {
      return null;
    }
    if (!logoDefaults) {
      logoDefaults = {
        node: note,
        standard: note.textContent,
        custom: note.getAttribute("data-note-custom"),
        tooBig: note.getAttribute("data-note-too-big"),
        noRoom: note.getAttribute("data-note-no-room")
      };
    }
    return logoDefaults;
  }

  function sayLogo(which) {
    var notes = logoNotes();
    if (!notes) {
      return;
    }
    var text = which === "standard" ? notes.standard : notes[which];
    if (text) {
      notes.node.textContent = text;
    }
  }

  function showLogo(url) {
    all("[data-bv-logo]").forEach(function (img) {
      if (!img.hasAttribute("data-bv-logo-default")) {
        img.setAttribute("data-bv-logo-default", img.getAttribute("src") || "");
      }
      img.setAttribute("src", url || img.getAttribute("data-bv-logo-default"));
    });
  }

  function restoreLogo() {
    var saved = readStore(LOGO_KEY);
    if (saved) {
      logoNotes();
      showLogo(saved);
      sayLogo("custom");
    }
  }

  function acceptLogo(input) {
    var file = input.files && input.files[0];
    if (!file) {
      return;
    }
    logoNotes();
    if (file.size > LOGO_LIMIT) {
      sayLogo("tooBig");
      input.value = "";
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      var url = String(reader.result || "");
      // Storage quota is a few megabytes in total and a base64 data URL is a third larger than
      // the file, so a file under the limit can still be refused. That is a report, not an error.
      if (!writeStore(LOGO_KEY, url)) {
        sayLogo("noRoom");
        return;
      }
      showLogo(url);
      sayLogo("custom");
    };
    reader.onerror = function () {
      sayLogo("standard");
    };
    reader.readAsDataURL(file);
  }

  function clearLogo() {
    logoNotes();
    dropStore(LOGO_KEY);
    showLogo("");
    sayLogo("standard");
    all(".bv-logo-file").forEach(function (input) {
      input.value = "";
    });
  }

  /* ------------------------------------------------------------------ 5. home-page card
   *
   * The card the page decorator adds, on the Jenkins home page and on job pages. Its placement and
   * its open / docked / closed state belong to home-card.js, which owns the drag, the clamping to
   * the viewport and the "bv-home-card" entry in storage. This file only does the part that
   * belongs to the preferences above, so neither script attaches a second handler to the dock,
   * close and reopen buttons or writes the other's state.
   *
   * The two scripts meet at the preference keys: the card asks whether it is wanted when it loads,
   * and these preferences live in the Build Stats app bar. So turning the plate off, or restricting
   * it to the home page, is reflected here at once for a card already on screen; home-card.js
   * applies the same two keys from scratch on the next page it loads on.
   *
   * Which pages the card may appear on is left to the stylesheet: it matches bv-status-home on
   * <html> against the card's own data-bv-page, so nothing has to be measured from here.
   */

  function applyHomeCard() {
    var card = document.querySelector("[data-bv-home]");
    if (!card) {
      return;
    }
    // Hidden rather than removed: this is a live preference, so unchecking and rechecking the box
    // has to bring the same card back without a reload.
    card.hidden = !prefOn("status");
  }

  /* ------------------------------------------------------------------ 6. chart hover
   *
   * The trend SVGs are drawn on the server. Each one already carries a transparent hit shape per
   * point, with the label and the values for that x (or that slice, or that bar) in data-tip.
   * A line chart carries the x of the sample and the y of every series, so the guide is a vertical
   * line through that x and a dot where the line meets each curve. A bar chart has no guide line:
   * the hovered bar stays bright and the others recede, and the tip sits on top of the bar. A pie
   * slice grows and the rest fade, with the tip beside the slice. The words were escaped into the
   * SVG with the page.
   */

  var chartTip = null;
  var activeSvg = null;
  var activeHit = null;

  function ensureTip() {
    if (chartTip) {
      return chartTip;
    }
    chartTip = el("div", "bv-tip");
    chartTip.setAttribute("role", "tooltip");
    chartTip.hidden = true;
    document.body.appendChild(chartTip);
    return chartTip;
  }

  function safeColor(value) {
    if (/^#[0-9A-Fa-f]{3,8}$/.test(value || "")) {
      return value;
    }
    if (/^var\(--[a-z0-9-]+,#[0-9A-Fa-f]{3,8}\)$/.test(value || "")) {
      return value;
    }
    return "";
  }

  function attrNum(node, name) {
    var value = parseFloat(node.getAttribute(name));
    return isNaN(value) ? 0 : value;
  }

  function looksLikeValue(value) {
    return /^(?:—|\d+(?:\.\d+)?(?:%|h|m)?)$/.test(value);
  }

  /** Split "Success 12" into a name and a value. Leave a phrase such as "12 runs" intact. */
  function splitValue(text) {
    var cut = text.lastIndexOf(" ");
    if (cut <= 0) {
      return looksLikeValue(text) ? { name: "", value: text } : { name: text, value: "" };
    }
    var value = text.slice(cut + 1);
    var name = text.slice(0, cut);
    if (looksLikeValue(value) && !/\d/.test(name)) {
      return { name: name, value: value };
    }
    return { name: text, value: "" };
  }

  function rowColors(hit, count) {
    var blank = [];
    var i;
    for (i = 0; i < count; i++) {
      blank.push("");
    }
    var raw = hit.getAttribute("data-colors");
    if (raw) {
      var listed = raw.split("|").map(safeColor);
      if (listed.length === count) {
        return listed;
      }
    }
    var one = safeColor(hit.getAttribute("data-color") || "");
    if (one && count > 0) {
      blank[0] = one;
      return blank;
    }
    var fromDots = [];
    (hit.getAttribute("data-ys") || "").split(";").forEach(function (part) {
      if (!part) {
        return;
      }
      fromDots.push(safeColor(part.split("|")[1] || ""));
    });
    if (fromDots.length === count) {
      return fromDots;
    }
    return blank;
  }

  function paintTip(hit) {
    var tip = ensureTip();
    clear(tip);
    var parts = (hit.getAttribute("data-tip") || "").split(/\s{2,}/).filter(function (part) {
      return part.length > 0;
    });
    if (!parts.length) {
      tip.hidden = true;
      return;
    }
    tip.hidden = false;
    tip.classList.toggle("bv-tip--pin", !hit.hasAttribute("data-x"));
    tip.classList.remove("bv-tip--bar");
    var rows = parts.slice(1);
    var colors = rowColors(hit, rows.length);
    // One series already shows its colour as the bar, so the tip is just the category and the value.
    if (isColumn(hit) && rows.length === 1 && !splitValue(rows[0]).name) {
      var alone = el("div", "bv-tip__row");
      alone.appendChild(el("span", "bv-tip__name", parts[0]));
      alone.appendChild(el("b", "bv-tip__value", rows[0]));
      tip.appendChild(alone);
      return;
    }
    tip.appendChild(el("div", "bv-tip__title", parts[0]));
    // A slice's count and share are one fact. Keep them on one line instead of a second row.
    if (hit.hasAttribute("data-x1") && rows.length > 1) {
      rows = [rows.join(" · ")];
      var sliceColor = "";
      colors.forEach(function (color) {
        if (!sliceColor && color) {
          sliceColor = color;
        }
      });
      colors = [sliceColor];
    }
    var anySwatch = colors.some(function (color) {
      return color;
    });
    rows.forEach(function (text, index) {
      var row = el("div", "bv-tip__row");
      var split = splitValue(text);
      if (anySwatch) {
        var swatch = el("i", "bv-tip__swatch");
        if (colors[index]) {
          swatch.style.background = colors[index];
        } else {
          swatch.style.visibility = "hidden";
        }
        row.appendChild(swatch);
      }
      if (split.name) {
        row.appendChild(el("span", "bv-tip__name", split.name));
      }
      if (split.value) {
        row.appendChild(el("b", "bv-tip__value", split.value));
      }
      tip.appendChild(row);
    });
  }

  function crossOf(svgNode) {
    var cross = svgNode.querySelector(".bv-cross");
    if (cross) {
      return cross;
    }
    cross = svg("g", { class: "bv-cross", visibility: "hidden", "pointer-events": "none" });
    cross.appendChild(svg("line", {
      class: "bv-cross-line",
      stroke: "currentColor",
      "stroke-opacity": "0.9"
    }));
    cross.appendChild(svg("g", { class: "bv-cross-dots" }));
    svgNode.appendChild(cross);
    return cross;
  }

  function showGuide(svgNode, x1, y1, x2, y2, dots) {
    var cross = crossOf(svgNode);
    var matrix = svgNode.getScreenCTM();
    var scale = matrix ? Math.abs(matrix.a) || 1 : 1;
    var line = cross.querySelector(".bv-cross-line");
    line.setAttribute("x1", x1);
    line.setAttribute("y1", y1);
    line.setAttribute("x2", x2);
    line.setAttribute("y2", y2);
    line.setAttribute("stroke-width", String(1 / scale));
    line.setAttribute("stroke-dasharray", (3 / scale) + " " + (3 / scale));
    var group = cross.querySelector(".bv-cross-dots");
    clear(group);
    var radius = 4.5 / scale;
    var stroke = 1.75 / scale;
    dots.forEach(function (dot) {
      group.appendChild(svg("circle", {
        cx: dot.x,
        cy: dot.y,
        r: radius,
        fill: dot.color || "currentColor",
        stroke: "var(--card-background,#fff)",
        "stroke-width": stroke
      }));
    });
    cross.setAttribute("visibility", "visible");
  }

  function lineDots(hit, x) {
    var dots = [];
    (hit.getAttribute("data-ys") || "").split(";").forEach(function (part) {
      if (!part) {
        return;
      }
      var bits = part.split("|");
      var color = safeColor(bits[1] || "");
      if (!color) {
        return;
      }
      dots.push({ x: x, y: bits[0], color: color });
    });
    return dots;
  }

  function isColumn(hit) {
    if (!hit || hit.tagName.toLowerCase() !== "rect") {
      return false;
    }
    if (hit.hasAttribute("data-x") || hit.hasAttribute("data-x1")) {
      return false;
    }
    var owner = hit.ownerSVGElement;
    if (owner && owner.classList.contains("bv-status")) {
      return false;
    }
    var width = attrNum(hit, "width");
    var height = attrNum(hit, "height");
    return height > 40 && height > width;
  }

  function columnGuide(svgNode, hit) {
    var x = attrNum(hit, "x");
    var y = attrNum(hit, "y");
    var width = attrNum(hit, "width");
    var height = attrNum(hit, "height");
    var top = null;
    var color = "currentColor";
    all("rect", svgNode).forEach(function (rect) {
      if (rect.classList.contains("bv-hit")) {
        return;
      }
      var rx = attrNum(rect, "x");
      var ry = attrNum(rect, "y");
      var rw = attrNum(rect, "width");
      var rh = attrNum(rect, "height");
      if (rw < 2 || rh < 2) {
        return;
      }
      if (rx < x - 0.6 || rx + rw > x + width + 0.6) {
        return;
      }
      if (ry < y - 0.6 || ry + rh > y + height + 0.6) {
        return;
      }
      if (top === null || ry < top) {
        top = ry;
        color = safeColor(rect.getAttribute("fill") || "") || "currentColor";
      }
    });
    return {
      x: x + width / 2,
      y1: y,
      y2: y + height,
      dots: top === null ? [] : [{ x: x + width / 2, y: top, color: color }]
    };
  }

  function focusSlice(svgNode, color) {
    all("circle", svgNode).forEach(function (circle) {
      if (circle.closest(".bv-cross")) {
        return;
      }
      var stroke = circle.getAttribute("stroke") || "";
      if (!safeColor(stroke)) {
        return;
      }
      var on = stroke.toLowerCase() === String(color).toLowerCase();
      circle.style.opacity = on ? "1" : "0.22";
      // Attribute, not CSS: a CSS length would be pixels and blow the ring up.
      // 18 is the width every slice is drawn at; 28 lifts the one under the pointer.
      circle.setAttribute("stroke-width", on ? "28" : "18");
    });
  }

  function isBarSegment(rect) {
    if (rect.classList.contains("bv-hit")) {
      return false;
    }
    var width = attrNum(rect, "width");
    var height = attrNum(rect, "height");
    // Legend swatches are 8×8 and sit below the plot.
    if (width === 8 && height === 8) {
      return false;
    }
    return width > 2 && height > 0.4;
  }

  /** Brighten the bars in this column and fade the rest. No guide line. */
  function emphasizeColumn(svgNode, hit) {
    var x = attrNum(hit, "x");
    var y = attrNum(hit, "y");
    var width = attrNum(hit, "width");
    var height = attrNum(hit, "height");
    all("rect", svgNode).forEach(function (rect) {
      if (!isBarSegment(rect)) {
        return;
      }
      var rx = attrNum(rect, "x");
      var ry = attrNum(rect, "y");
      var rw = attrNum(rect, "width");
      var rh = attrNum(rect, "height");
      if (ry + rh < y - 0.6 || ry > y + height + 0.6) {
        return;
      }
      var inside = rx >= x - 0.6 && rx + rw <= x + width + 0.6;
      rect.classList.toggle("bv-bar-on", inside);
      rect.classList.toggle("bv-bar-dim", !inside);
    });
  }

  function clearGuide(svgNode) {
    if (!svgNode) {
      return;
    }
    var cross = svgNode.querySelector(".bv-cross");
    if (cross) {
      cross.setAttribute("visibility", "hidden");
      var dots = cross.querySelector(".bv-cross-dots");
      if (dots) {
        clear(dots);
      }
    }
    all(".bv-hit--on", svgNode).forEach(function (node) {
      node.classList.remove("bv-hit--on");
    });
    all(".bv-bar-on, .bv-bar-dim", svgNode).forEach(function (rect) {
      rect.classList.remove("bv-bar-on");
      rect.classList.remove("bv-bar-dim");
    });
    all("circle", svgNode).forEach(function (circle) {
      circle.style.opacity = "";
      if (circle.getAttribute("stroke-width") === "28") {
        circle.setAttribute("stroke-width", "18");
      }
    });
  }

  function showHit(svgNode, hit) {
    clearGuide(svgNode);
    if (hit.hasAttribute("data-x")) {
      hit.classList.add("bv-hit--on");
      var x = hit.getAttribute("data-x");
      showGuide(svgNode, x, hit.getAttribute("data-top"), x, hit.getAttribute("data-bottom"), lineDots(hit, x));
      return;
    }
    if (hit.hasAttribute("data-x1")) {
      var color = safeColor(hit.getAttribute("data-color") || "");
      if (color) {
        focusSlice(svgNode, color);
      }
      return;
    }
    if (isColumn(hit)) {
      var cap = columnGuide(svgNode, hit);
      showGuide(svgNode, cap.x, cap.y1, cap.x, cap.y2, cap.dots);
      return;
    }
    hit.classList.add("bv-hit--on");
  }

  function toScreen(svgNode, x, y) {
    var matrix = svgNode.getScreenCTM();
    if (!matrix || !svgNode.createSVGPoint) {
      return null;
    }
    var point = svgNode.createSVGPoint();
    point.x = x;
    point.y = y;
    var mapped = point.matrixTransform(matrix);
    return { x: mapped.x, y: mapped.y };
  }

  function anchorOf(svgNode, hit, event) {
    var point = null;
    if (hit.hasAttribute("data-x")) {
      point = toScreen(svgNode, parseFloat(hit.getAttribute("data-x")), parseFloat(hit.getAttribute("data-top")));
    } else if (hit.hasAttribute("data-x2")) {
      point = toScreen(svgNode, parseFloat(hit.getAttribute("data-x2")), parseFloat(hit.getAttribute("data-y2")));
    } else if (isColumn(hit)) {
      var cap = columnGuide(svgNode, hit);
      point = toScreen(svgNode, cap.x, cap.y1);
    }
    if (!point) {
      point = { x: event.clientX, y: event.clientY };
    }
    return point;
  }

  function placeTip(svgNode, hit, event) {
    var tip = ensureTip();
    if (tip.hidden) {
      return;
    }
    var anchor = anchorOf(svgNode, hit, event);
    var box = tip.getBoundingClientRect();
    var x;
    var y;
    var outside = null;
    var card = hit.closest(".bv-chart");
    // The dashed guide stays on the drawing. The tip sits outside that drawing: above a line or
    // a bar, and radially outside a slice, so the numbers never cover the chart itself.
    if (hit.hasAttribute("data-x") || isColumn(hit)) {
      var parked = placeOffPlot(anchor, box, plotScreenBox(svgNode), card, svgNode);
      x = parked.x;
      y = parked.y;
    } else if (hit.hasAttribute("data-x1")) {
      outside = placeOutsideRing(svgNode, hit, box);
      x = outside ? outside.x : anchor.x - box.width / 2;
      y = outside ? outside.y : anchor.y - box.height - 10;
    } else {
      x = anchor.x - box.width / 2;
      y = anchor.y - box.height - 8;
      var clamped = clampToCard(x, y, box, card);
      x = clamped.x;
      y = clamped.y;
    }
    if (x + box.width > window.innerWidth - 8) {
      x = window.innerWidth - box.width - 8;
    }
    if (y + box.height > window.innerHeight - 8) {
      // A slice stays on its outward side. Pulling it back above the anchor lands it on the ring.
      y = hit.hasAttribute("data-x1")
        ? window.innerHeight - box.height - 8
        : Math.max(8, y);
    }
    if (y < 8) {
      y = 8;
    }
    if (x < 8) {
      x = 8;
    }
    tip.style.left = x + "px";
    tip.style.top = y + "px";
  }

  function plotScreenBox(svgNode) {
    var hits = svgNode.querySelectorAll(".bv-hit");
    var left = Infinity;
    var top = Infinity;
    var right = -Infinity;
    var bottom = -Infinity;
    for (var i = 0; i < hits.length; i++) {
      var rect = hits[i].getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) {
        continue;
      }
      if (rect.left < left) {
        left = rect.left;
      }
      if (rect.top < top) {
        top = rect.top;
      }
      if (rect.right > right) {
        right = rect.right;
      }
      if (rect.bottom > bottom) {
        bottom = rect.bottom;
      }
    }
    if (left === Infinity) {
      return svgNode.getBoundingClientRect();
    }
    return { left: left, top: top, right: right, bottom: bottom };
  }

  function overlapsPlot(x, y, box, plot) {
    return x < plot.right && x + box.width > plot.left && y < plot.bottom && y + box.height > plot.top;
  }

  function clampToCard(x, y, box, card) {
    if (!card) {
      return { x: x, y: y };
    }
    var bounds = card.getBoundingClientRect();
    if (x < bounds.left + 6) {
      x = bounds.left + 6;
    }
    if (x + box.width > bounds.right - 6) {
      x = bounds.right - box.width - 6;
    }
    return { x: x, y: y };
  }

  function coversAnotherPlot(x, y, box, svgNode) {
    var plots = document.querySelectorAll(".bv-chart__plot svg");
    for (var i = 0; i < plots.length; i++) {
      if (plots[i] === svgNode) {
        continue;
      }
      if (overlapsPlot(x, y, box, plotScreenBox(plots[i]))) {
        return true;
      }
    }
    return false;
  }

  /** Prefer above the drawing, then below it. Either way the box stays off every plotted area. */
  function placeOffPlot(anchor, box, plot, card, svgNode) {
    var gap = 8;
    var above = clampToCard(anchor.x - box.width / 2, plot.top - box.height - gap, box, card);
    if (above.y >= 8 && !overlapsPlot(above.x, above.y, box, plot) && !coversAnotherPlot(above.x, above.y, box, svgNode)) {
      return above;
    }
    var below = clampToCard(anchor.x - box.width / 2, plot.bottom + gap, box, card);
    if (!overlapsPlot(below.x, below.y, box, plot) && !coversAnotherPlot(below.x, below.y, box, svgNode)) {
      return below;
    }
    return above.y >= 8 ? above : below;
  }

  /**
   * Put the pie tip on the outside of the ring, along the slice's own direction, so it cannot
   * cover the hole or the slice it is describing.
   */
  function placeOutsideRing(svgNode, hit, box) {
    var ring = svgNode.querySelector("circle[cx]");
    if (!ring) {
      return null;
    }
    var center = toScreen(svgNode, parseFloat(ring.getAttribute("cx")), parseFloat(ring.getAttribute("cy")));
    var outer = toScreen(svgNode, parseFloat(hit.getAttribute("data-x2")), parseFloat(hit.getAttribute("data-y2")));
    if (!center || !outer) {
      return null;
    }
    var dx = outer.x - center.x;
    var dy = outer.y - center.y;
    var len = Math.sqrt(dx * dx + dy * dy) || 1;
    var ux = dx / len;
    var uy = dy / len;
    var reach = Math.abs(ux) * box.width / 2 + Math.abs(uy) * box.height / 2;
    var dist = len + 16 + reach;
    return {
      x: center.x + ux * dist - box.width / 2,
      y: center.y + uy * dist - box.height / 2
    };
  }

  function hideChartTip() {
    if (chartTip) {
      chartTip.hidden = true;
    }
    clearGuide(activeSvg);
    activeSvg = null;
    activeHit = null;
  }

  function onPointerMove(event) {
    var target = event.target;
    var hit = target && target.closest ? target.closest("svg .bv-hit") : null;
    if (!hit || !hit.closest("#main-panel")) {
      hideChartTip();
      return;
    }
    var svgNode = hit.ownerSVGElement;
    if (activeHit !== hit) {
      if (activeSvg && activeSvg !== svgNode) {
        clearGuide(activeSvg);
      }
      activeSvg = svgNode;
      activeHit = hit;
      paintTip(hit);
      showHit(svgNode, hit);
    }
    placeTip(svgNode, hit, event);
  }

  /* ------------------------------------------------------------------ wiring
   *
   * Two delegated listeners on the document instead of one per control. Several of these regions
   * are rebuilt while the page is open (the GPU slot, the core grid, the disk list), and a
   * delegated listener keeps working across that without anything having to re-attach.
   */

  function onChange(event) {
    var target = event.target;
    if (!target || !target.closest) {
      return;
    }

    var pref = target.closest("input.bv-pref[data-pref]");
    if (pref) {
      choosePref(pref.getAttribute("data-pref"), pref.checked);
      return;
    }

    var where = target.closest('select.bv-pref-select[data-pref="statusWhere"]');
    if (where) {
      chooseWhere(where.value);
      return;
    }

    var go = target.closest("select.bv-go");
    if (go) {
      navigateFrom(go);
      return;
    }

    var logo = target.closest(".bv-logo-file");
    if (logo) {
      acceptLogo(logo);
    }
  }

  function onClick(event) {
    var target = event.target;
    if (!target || !target.closest) {
      return;
    }

    var lang = target.closest("[data-bv-lang]");
    if (lang) {
      var code = lang.getAttribute("data-bv-lang");
      if (code === "zh" || code === "en") {
        document.cookie = "bv-lang=" + code + "; path=/; max-age=31536000; SameSite=Lax";
        window.location.reload();
      }
      return;
    }

    var mode = target.closest("[data-bv-cpu-mode]");
    if (mode) {
      setCpuCores(mode.getAttribute("data-bv-cpu-mode") === "cores");
      return;
    }

    if (target.closest("[data-bv-logo-clear]")) {
      clearLogo();
      return;
    }

    // The card's own dock, close and reopen buttons are home-card.js's: see section 5.
  }

  function ready() {
    reflectPrefControls();
    restoreLogo();

    // Sampling happens only where the cards to fill actually exist, so the overview and About
    // pages — and every page the decorator's card rides on — issue no requests at all.
    if (document.querySelector("[data-live]")) {
      pace = 1;
      setCpuCores(false);
      setSamplerState(false);
      sampling = true;
      startSampling();
    }
  }

  // The preference classes go on before anything else so the stored choice is already in force
  // when the browser paints the elements the stylesheet hides.
  applyPrefClasses();

  document.addEventListener("change", onChange);
  document.addEventListener("click", onClick);
  document.addEventListener("pointermove", onPointerMove);
  window.addEventListener("scroll", hideChartTip, true);
  window.addEventListener("blur", hideChartTip);

  // A hidden tab does not need a controller to keep measuring its own CPU. The timer is dropped on
  // the way out and a fresh sample is taken on the way back, so the first thing a returning reader
  // sees is current rather than minutes old.
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "hidden") {
      stopSampling();
    } else {
      startSampling();
    }
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", ready);
  } else {
    ready();
  }
})();
