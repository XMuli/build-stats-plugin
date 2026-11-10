/*
 * The status card that floats on the Jenkins home page and on a job page.
 *
 * Placement only: where the card sits, whether it is docked, closed or open, and remembering that
 * in this browser. All of its text and artwork are rendered by footer.jelly for the request's
 * locale, so nothing here reads or rewrites language — the older version carried its own
 * translation table and an onboarding bubble, and both are gone.
 */
(function () {
  "use strict";

  // The page and the decorator can both pull this file in; only the first run wires anything up.
  if (window.__bvHomeCard) {
    return;
  }
  window.__bvHomeCard = true;

  var PLACE = "bv-home-card";
  var SHOW = "bv-sign-show";
  var WHERE = "bv-sign-where";
  var EDGE = 8;
  var SNAP = 72;
  var CARD_WIDTH = 240;

  var root = document.querySelector("[data-bv-home]");
  if (!root) {
    return;
  }

  var plate = root.querySelector(".bv-plate");
  if (!plate) {
    return;
  }

  function stored(key) {
    try {
      return localStorage.getItem(key);
    } catch (e) {
      // Storage is unavailable in some privacy modes. Fall back to the default state.
      return null;
    }
  }

  function store(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch (e) {
      // Nothing to do: the card still works, it just will not remember this.
    }
  }

  /** The two preferences from the Build Stats app bar also govern this card. */
  function wanted() {
    if (stored(SHOW) === "0") {
      return false;
    }
    var page = root.getAttribute("data-bv-page") || "home";
    return !(stored(WHERE) === "home" && page !== "home");
  }

  if (!wanted()) {
    root.remove();
    return;
  }

  // Fixed positioning only behaves predictably from the body, not from wherever the page decorator
  // happened to be inserted.
  document.body.appendChild(root);

  var close = root.querySelector("[data-bv-home-close]");
  var reopen = root.querySelector("[data-bv-home-reopen]");

  function readPlace() {
    try {
      var raw = JSON.parse(stored(PLACE) || "{}");
      return {
        mode: raw.mode === "peek" || raw.mode === "closed" ? raw.mode : "open",
        side: raw.side === "left" ? "left" : "right",
        left: typeof raw.left === "number" ? raw.left : null,
        top: typeof raw.top === "number" ? raw.top : null
      };
    } catch (e) {
      return { mode: "open", side: "right", left: null, top: null };
    }
  }

  function savePlace() {
    store(PLACE, JSON.stringify(state));
  }

  /** Keep the card on screen whatever the window has done since it was placed. */
  function clamp(left, top) {
    var box = root.getBoundingClientRect();
    return {
      left: Math.max(EDGE, Math.min(Math.max(EDGE, window.innerWidth - box.width - EDGE), left)),
      top: Math.max(EDGE, Math.min(Math.max(EDGE, window.innerHeight - box.height - EDGE), top))
    };
  }

  function clampTop(top) {
    var height = root.getBoundingClientRect().height || 48;
    return Math.max(EDGE, Math.min(Math.max(EDGE, window.innerHeight - height - EDGE), top));
  }

  /** Which vertical edge the card is touching, if either. */
  function nearEdge(left, width) {
    if (left <= SNAP) {
      return "left";
    }
    if (left + width >= window.innerWidth - SNAP) {
      return "right";
    }
    return null;
  }

  function placeOpen() {
    var width = root.classList.contains("bv-home--peek") ? CARD_WIDTH : root.getBoundingClientRect().width;
    return state.side === "left" ? EDGE : Math.max(EDGE, window.innerWidth - (width || CARD_WIDTH) - EDGE);
  }

  function apply() {
    root.classList.toggle("bv-home--peek", state.mode === "peek");
    root.classList.toggle("bv-home--closed", state.mode === "closed");
    root.classList.toggle("bv-home--left", state.side === "left");
    if (reopen) {
      reopen.hidden = state.mode !== "closed";
    }
    root.style.left = "";
    root.style.top = "";
    root.style.right = "";
    root.style.bottom = "";
    if (state.mode === "peek") {
      var parked = state.top == null ? EDGE : clampTop(state.top);
      state.top = parked;
      root.style.top = parked + "px";
      root.style.bottom = "auto";
      root.style.right = "auto";
      // Sit flush with the edge. The transform then eases the rest of the card off screen.
      root.style.left = (state.side === "left" ? 0 : Math.max(0, window.innerWidth - CARD_WIDTH)) + "px";
      return;
    }
    if (state.mode === "open" && state.left != null && state.top != null) {
      var spot = clamp(state.left, state.top);
      state.left = spot.left;
      state.top = spot.top;
      root.style.left = spot.left + "px";
      root.style.top = spot.top + "px";
      root.style.right = "auto";
      root.style.bottom = "auto";
    }
    paintDock();
  }

  function dockPreview() {
    var value = new URLSearchParams(window.location.search).get("dock");
    if (value === "b" || value === "c") {
      return value;
    }
    return "a";
  }

  function paintDock() {
    var style = dockPreview();
    root.classList.remove("bv-dock--a", "bv-dock--b", "bv-dock--c");
    var dock = root.querySelector(".bv-dock");
    if (!style || !dock) {
      if (dock) {
        dock.hidden = true;
      }
      return;
    }
    root.classList.add("bv-dock--" + style);
    var source = root.querySelector(".bv-plate__img");
    var image = dock.querySelector(".bv-dock__img");
    if (source && image) {
      image.src = source.getAttribute("src") || "";
    }
    var build = root.querySelector(".bv-plate__build");
    var number = dock.querySelector(".bv-dock__num");
    if (build && number) {
      number.textContent = build.textContent.replace(/\s*↗\s*/g, "").trim();
    }
    var badge = root.querySelector(".bv-plate__state");
    var stateLabel = dock.querySelector(".bv-dock__state");
    if (badge && stateLabel) {
      stateLabel.textContent = badge.textContent.trim();
      stateLabel.className = "bv-dock__state " + badge.className.replace("bv-plate__state", "").trim();
    }
    dock.hidden = state.mode !== "peek" || root.classList.contains("bv-home--reach");
  }

  function expandParked() {
    if (drag || state.mode !== "peek" || root.classList.contains("bv-home--reach")) {
      return;
    }
    root.classList.add("bv-home--reach");
    paintDock();
    var sign = state.side === "left" ? "-28%" : "28%";
    root.style.transition = "none";
    root.style.transform = "translateX(" + sign + ")";
    window.requestAnimationFrame(function () {
      window.requestAnimationFrame(function () {
        root.style.transition = "";
        root.style.transform = "translateX(0)";
      });
    });
  }

  function parkAgain() {
    if (drag || state.mode !== "peek") {
      return;
    }
    root.classList.remove("bv-home--reach");
    root.style.transform = "";
    paintDock();
  }

  var state = readPlace();
  var preview = dockPreview();
  if (preview) {
    state.mode = "peek";
    state.side = "right";
    if (state.top == null) {
      state.top = 160;
    }
  }
  apply();
  paintDock();

  if (close) {
    close.addEventListener("click", function () {
      state.mode = "closed";
      savePlace();
      apply();
      // Move focus somewhere real, so it does not vanish with the button that was pressed.
      if (reopen) {
        reopen.focus();
      }
    });
  }

  function openOut() {
    state.mode = "open";
    state.left = placeOpen();
    if (state.top == null) {
      state.top = Math.max(EDGE, window.innerHeight - 280);
    }
    savePlace();
    apply();
  }

  if (reopen) {
    reopen.addEventListener("click", function () {
      openOut();
      if (close) {
        close.focus();
      }
    });
  }

  // Clicking a docked card brings it back, without swallowing clicks on its own controls.
  root.addEventListener("click", function (event) {
    if (state.mode !== "peek") {
      return;
    }
    if (event.target.closest("button") || event.target.closest("a")) {
      return;
    }
    openOut();
  });

  // Dragging. A press that never moves far is left alone so links still work; once it does move,
  // the pointer is captured and the click is suppressed on release.
  var drag = null;

  plate.addEventListener("dragstart", function (event) {
    event.preventDefault();
  });

  function beginDrag(event) {
    if (event.button != null && event.button !== 0) {
      return;
    }
    if (event.target.closest("button") || event.target.closest("a")) {
      return;
    }
    if (state.mode === "closed") {
      return;
    }
    var box = root.getBoundingClientRect();
    var fromPeek = state.mode === "peek" && !root.classList.contains("bv-home--reach");
    drag = {
      id: event.pointerId,
      fromX: event.clientX,
      fromY: event.clientY,
      offsetX: event.clientX - box.left,
      offsetY: event.clientY - box.top,
      fromPeek: fromPeek,
      side: state.side,
      moved: false,
      captured: false
    };
    if (fromPeek) {
      plate.setPointerCapture(event.pointerId);
      drag.captured = true;
    }
  }

  plate.addEventListener("pointerdown", beginDrag);
  root.addEventListener("pointerdown", function (event) {
    if (state.mode === "peek") {
      beginDrag(event);
    }
  });

  plate.addEventListener("pointermove", onDrag);
  root.addEventListener("pointermove", onDrag);

  function onDrag(event) {
    if (!drag || drag.id !== event.pointerId) {
      return;
    }
    if (!drag.moved
        && Math.abs(event.clientX - drag.fromX) < 4
        && Math.abs(event.clientY - drag.fromY) < 4) {
      return;
    }
    if (!drag.captured) {
      plate.setPointerCapture(event.pointerId);
      drag.captured = true;
    }
    if (drag.fromPeek) {
      drag.fromPeek = false;
      drag.moved = true;
      state.mode = "open";
      root.classList.remove("bv-home--peek", "bv-home--left");
      root.classList.add("bv-home--dragging");
      var width = CARD_WIDTH;
      var left = Math.min(Math.max(EDGE, event.clientX - width * 0.35), window.innerWidth - width - EDGE);
      var top = Math.min(Math.max(EDGE, event.clientY - 48), window.innerHeight - 160);
      drag.offsetX = event.clientX - left;
      drag.offsetY = event.clientY - top;
      root.style.left = left + "px";
      root.style.top = top + "px";
      root.style.right = "auto";
      root.style.bottom = "auto";
      state.left = left;
      state.top = top;
      return;
    }
    drag.moved = true;
    var rawLeft = event.clientX - drag.offsetX;
    var rawTop = event.clientY - drag.offsetY;
    root.classList.add("bv-home--dragging");
    state.mode = "open";
    root.classList.remove("bv-home--peek", "bv-home--left");
    var spot = clamp(rawLeft, rawTop);
    root.style.left = spot.left + "px";
    root.style.top = spot.top + "px";
    root.style.right = "auto";
    root.style.bottom = "auto";
    state.left = spot.left;
    state.top = spot.top;
  }

  function endDrag(event) {
    if (!drag || drag.id !== event.pointerId) {
      return;
    }
    var moved = drag.moved;
    drag = null;
    root.classList.remove("bv-home--dragging");
    if (moved) {
      var edge = nearEdge(state.left, root.getBoundingClientRect().width);
      var over = root.matches(":hover");
      if (edge) {
        state.mode = "peek";
        state.side = edge;
        state.top = root.getBoundingClientRect().top;
        if (over) {
          root.classList.add("bv-home--reach");
        } else {
          root.classList.remove("bv-home--reach");
        }
      } else {
        state.mode = "open";
        root.classList.remove("bv-home--reach");
      }
      savePlace();
      apply();
      plate.dataset.dragged = "1";
    }
  }

  plate.addEventListener("pointerup", endDrag);
  plate.addEventListener("pointercancel", endDrag);
  root.addEventListener("pointerup", endDrag);
  root.addEventListener("pointercancel", endDrag);

  root.addEventListener("pointerenter", function () {
    expandParked();
  });

  root.addEventListener("pointerleave", function () {
    parkAgain();
  });

  plate.addEventListener("click", function (event) {
    if (plate.dataset.dragged === "1") {
      event.preventDefault();
      event.stopPropagation();
      delete plate.dataset.dragged;
    }
  }, true);

  /** The card's own link already opens Build Stats. Double-click and right-click do the same. */
  function openStats() {
    if (plate.dataset.dragged === "1" || state.mode === "closed") {
      return;
    }
    var href = root.getAttribute("data-bv-stats");
    if (href) {
      window.location.assign(href);
    }
  }

  root.addEventListener("dblclick", function (event) {
    if (event.target.closest("button")) {
      return;
    }
    openStats();
  });

  root.addEventListener("contextmenu", function (event) {
    if (event.target.closest("button") || state.mode === "closed") {
      return;
    }
    event.preventDefault();
    openStats();
  });

  window.addEventListener("resize", function () {
    if (state.mode !== "open" || state.left == null || state.top == null) {
      return;
    }
    var spot = clamp(state.left, state.top);
    state.left = spot.left;
    state.top = spot.top;
    savePlace();
    apply();
  });
})();
