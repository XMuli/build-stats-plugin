(function () {
  if (window.__bvHome) {
    return;
  }
  window.__bvHome = true;
  var root = document.querySelector("[data-bv-home]");
  var guide = document.querySelector(".bv-entry-guide");
  if (!root || !guide) {
    return;
  }
  document.body.appendChild(root);
  document.body.appendChild(guide);
  var card = root.querySelector(".bv-home-card");
  card.querySelectorAll("img").forEach(function (img) {
    img.draggable = false;
  });
  var text = guide.querySelector(".bv-entry-text");
  var ok = guide.querySelector(".bv-entry-ok");
  var again = guide.querySelector(".bv-entry-show");
  var arrow = guide.querySelector(".bv-entry-arrow");
  var KEY = "bv-home-card";
  var INTRO = "bv-entry-intro";
  var ACK = "bv-entry-closed-ack";
  var copy = {
    zh: {
      peek: "收到右侧",
      close: "关闭",
      reopen: "显示状态牌",
      intro: "右上角这个图标可以进入构建统计，也可以从插件页离开。",
      closed: "首页卡片已关闭。之后用右上角这个图标进入或离开构建统计。",
      ok: "知道了",
      again: "重新显示卡片"
    },
      en: {
      peek: "Dock right",
      close: "Close",
      reopen: "Card",
      intro: "Use the icon at the top right to open Build Stats, and to leave it.",
      closed: "The home card is closed. Use the icon at the top right to open or leave Build Stats.",
      ok: "Got it",
      again: "Show the card again"
    }
  };

  function lang() {
    try {
      var saved = localStorage.getItem("bv-lang");
      if (saved === "zh" || saved === "en") {
        return saved;
      }
    } catch (e) {}
    return "en";
  }

  function words() {
    return copy[lang()];
  }

  function read() {
    try {
      var raw = JSON.parse(localStorage.getItem(KEY) || "{}");
      return {
        mode: raw.mode === "peek" || raw.mode === "closed" ? raw.mode : "open",
        left: typeof raw.left === "number" ? raw.left : null,
        top: typeof raw.top === "number" ? raw.top : null
      };
    } catch (e) {
      return { mode: "open", left: null, top: null };
    }
  }

  function save(state) {
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch (e) {}
  }

  function seen(key) {
    try {
      return localStorage.getItem(key) === "1";
    } catch (e) {
      return false;
    }
  }

  function mark(key) {
    try {
      localStorage.setItem(key, "1");
    } catch (e) {}
  }

  function clearMark(key) {
    try {
      localStorage.removeItem(key);
    } catch (e) {}
  }

  function entryLink() {
    return document.querySelector('a[href$="build-stats"]');
  }

  function apply(state) {
    root.classList.remove("is-peek", "is-closed");
    root.style.left = "";
    root.style.top = "";
    root.style.right = "";
    root.style.bottom = "";
    if (state.mode === "peek") {
      root.classList.add("is-peek");
      if (state.top != null) {
        root.style.top = state.top + "px";
      }
      return;
    }
    if (state.mode === "closed") {
      root.classList.add("is-closed");
      return;
    }
    if (state.left != null && state.top != null) {
      var spot = clamp(state.left, state.top);
      root.style.left = spot.left + "px";
      root.style.top = spot.top + "px";
      root.style.right = "auto";
      root.style.bottom = "auto";
    }
  }

  function clamp(left, top) {
    var rect = root.getBoundingClientRect();
    var maxLeft = Math.max(8, window.innerWidth - rect.width - 8);
    var maxTop = Math.max(8, window.innerHeight - rect.height - 8);
    return {
      left: Math.max(8, Math.min(maxLeft, left)),
      top: Math.max(8, Math.min(maxTop, top))
    };
  }

  function hideGuide() {
    guide.hidden = true;
    var link = entryLink();
    if (link) {
      link.classList.remove("bv-entry-target");
    }
  }

  function showGuide(kind) {
    var message = words();
    text.textContent = message[kind];
    ok.textContent = message.ok;
    again.textContent = message.again;
    again.hidden = kind !== "closed";
    guide.hidden = false;
    var link = entryLink();
    if (link) {
      link.classList.add("bv-entry-target");
    }
    requestAnimationFrame(function () {
      var width = guide.offsetWidth;
      if (!link) {
        guide.style.top = "72px";
        guide.style.right = "16px";
        guide.style.left = "auto";
        arrow.style.right = "22px";
        arrow.style.left = "auto";
        return;
      }
      var rect = link.getBoundingClientRect();
      var left = Math.min(window.innerWidth - width - 12, Math.max(12, rect.right - width + 8));
      guide.style.left = left + "px";
      guide.style.top = rect.bottom + 12 + "px";
      guide.style.right = "auto";
      arrow.style.left = Math.max(16, Math.min(width - 28, rect.left + rect.width / 2 - left - 6)) + "px";
      arrow.style.right = "auto";
    });
  }

  function label() {
    var message = words();
    root.querySelector(".bv-home-peek").setAttribute("aria-label", message.peek);
    root.querySelector(".bv-home-close").setAttribute("aria-label", message.close);
    root.querySelector(".bv-home-reopen").textContent = message.reopen;
  }

  var state = read();
  label();
  apply(state);
  if (state.mode === "closed" && !seen(ACK)) {
    showGuide("closed");
  } else if (!seen(INTRO)) {
    showGuide("intro");
  }

  root.querySelector(".bv-home-peek").addEventListener("click", function (event) {
    event.preventDefault();
    event.stopPropagation();
    var rect = root.getBoundingClientRect();
    state.mode = "peek";
    state.top = rect.top;
    save(state);
    hideGuide();
    apply(state);
  });

  root.querySelector(".bv-home-close").addEventListener("click", function (event) {
    event.preventDefault();
    event.stopPropagation();
    state.mode = "closed";
    save(state);
    clearMark(ACK);
    mark(INTRO);
    apply(state);
    showGuide("closed");
  });

  root.querySelector(".bv-home-reopen").addEventListener("click", function () {
    state.mode = "open";
    save(state);
    mark(ACK);
    mark(INTRO);
    hideGuide();
    apply(state);
  });

  root.addEventListener("click", function (event) {
    if (!root.classList.contains("is-peek")) {
      return;
    }
    if (event.target.closest("button")) {
      return;
    }
    event.preventDefault();
    state.mode = "open";
    save(state);
    apply(state);
  });

  ok.addEventListener("click", function () {
    if (state.mode === "closed") {
      mark(ACK);
    }
    mark(INTRO);
    hideGuide();
  });

  again.addEventListener("click", function () {
    state.mode = "open";
    save(state);
    mark(ACK);
    mark(INTRO);
    hideGuide();
    apply(state);
  });

  var drag = null;
  card.addEventListener("pointerdown", function (event) {
    if (event.button != null && event.button !== 0) {
      return;
    }
    if (event.target.closest("button") || root.classList.contains("is-peek") || root.classList.contains("is-closed")) {
      return;
    }
    var rect = root.getBoundingClientRect();
    drag = {
      id: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      dx: event.clientX - rect.left,
      dy: event.clientY - rect.top,
      moved: false
    };
    card.setPointerCapture(event.pointerId);
  });

  card.addEventListener("dragstart", function (event) {
    event.preventDefault();
  });

  card.addEventListener("pointermove", function (event) {
    if (!drag || drag.id !== event.pointerId) {
      return;
    }
    if (Math.abs(event.clientX - drag.x) > 4 || Math.abs(event.clientY - drag.y) > 4) {
      drag.moved = true;
    }
    if (!drag.moved) {
      return;
    }
    var spot = clamp(event.clientX - drag.dx, event.clientY - drag.dy);
    root.classList.add("is-dragging");
    root.style.left = spot.left + "px";
    root.style.top = spot.top + "px";
    root.style.right = "auto";
    root.style.bottom = "auto";
    state.left = spot.left;
    state.top = spot.top;
    state.mode = "open";
  });

  function endDrag(event) {
    if (!drag || drag.id !== event.pointerId) {
      return;
    }
    var didMove = drag.moved;
    drag = null;
    root.classList.remove("is-dragging");
    if (didMove) {
      save(state);
      card.dataset.dragged = "1";
    }
  }

  card.addEventListener("pointerup", endDrag);
  card.addEventListener("pointercancel", endDrag);
  card.querySelectorAll("a").forEach(function (link) {
    link.addEventListener("click", function (event) {
      if (card.dataset.dragged === "1") {
        event.preventDefault();
      }
    });
  });
  card.addEventListener("click", function () {
    delete card.dataset.dragged;
  });

  window.addEventListener("resize", function () {
    if (state.mode !== "open" || state.left == null || state.top == null) {
      return;
    }
    var spot = clamp(state.left, state.top);
    state.left = spot.left;
    state.top = spot.top;
    save(state);
    apply(state);
  });
})();
