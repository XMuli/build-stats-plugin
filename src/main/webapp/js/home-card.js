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

  var dock = root.querySelector("[data-bv-home-dock]");
  var close = root.querySelector("[data-bv-home-close]");
  var reopen = root.querySelector("[data-bv-home-reopen]");

  function readPlace() {
    try {
      var raw = JSON.parse(stored(PLACE) || "{}");
      return {
        mode: raw.mode === "peek" || raw.mode === "closed" ? raw.mode : "open",
        left: typeof raw.left === "number" ? raw.left : null,
        top: typeof raw.top === "number" ? raw.top : null
      };
    } catch (e) {
      return { mode: "open", left: null, top: null };
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

  function apply() {
    root.classList.toggle("bv-home--peek", state.mode === "peek");
    root.classList.toggle("bv-home--closed", state.mode === "closed");
    if (reopen) {
      reopen.hidden = state.mode !== "closed";
    }
    root.style.left = "";
    root.style.top = "";
    root.style.right = "";
    root.style.bottom = "";
    if (state.mode === "open" && state.left != null && state.top != null) {
      var spot = clamp(state.left, state.top);
      root.style.left = spot.left + "px";
      root.style.top = spot.top + "px";
      root.style.right = "auto";
      root.style.bottom = "auto";
    }
  }

  var state = readPlace();
  apply();

  if (dock) {
    dock.addEventListener("click", function () {
      state.mode = "peek";
      state.top = root.getBoundingClientRect().top;
      savePlace();
      apply();
    });
  }

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

  if (reopen) {
    reopen.addEventListener("click", function () {
      state.mode = "open";
      savePlace();
      apply();
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
    state.mode = "open";
    savePlace();
    apply();
  });

  // Dragging. A press that never moves far is left alone so links still work; once it does move,
  // the pointer is captured and the click is suppressed on release.
  var drag = null;

  plate.addEventListener("dragstart", function (event) {
    event.preventDefault();
  });

  plate.addEventListener("pointerdown", function (event) {
    if (event.button != null && event.button !== 0) {
      return;
    }
    if (event.target.closest("button") || state.mode !== "open") {
      return;
    }
    var box = root.getBoundingClientRect();
    drag = {
      id: event.pointerId,
      fromX: event.clientX,
      fromY: event.clientY,
      offsetX: event.clientX - box.left,
      offsetY: event.clientY - box.top,
      moved: false,
      captured: false
    };
  });

  plate.addEventListener("pointermove", function (event) {
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
    drag.moved = true;
    var spot = clamp(event.clientX - drag.offsetX, event.clientY - drag.offsetY);
    root.classList.add("bv-home--dragging");
    root.style.left = spot.left + "px";
    root.style.top = spot.top + "px";
    root.style.right = "auto";
    root.style.bottom = "auto";
    state.left = spot.left;
    state.top = spot.top;
  });

  function endDrag(event) {
    if (!drag || drag.id !== event.pointerId) {
      return;
    }
    var moved = drag.moved;
    drag = null;
    root.classList.remove("bv-home--dragging");
    if (moved) {
      savePlace();
      plate.dataset.dragged = "1";
    }
  }

  plate.addEventListener("pointerup", endDrag);
  plate.addEventListener("pointercancel", endDrag);

  plate.addEventListener("click", function (event) {
    if (plate.dataset.dragged === "1") {
      event.preventDefault();
      delete plate.dataset.dragged;
    }
  }, true);

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
