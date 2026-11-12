// Revalidate protected pages restored through browser Back/Forward.
const sensitiveHistoryPath =
  /^\/(?:customer(?:\/|$)|manager(?:\/|$))/.test(window.location.pathname);

if (sensitiveHistoryPath) {
  window.addEventListener("pageshow", (event) => {
    if (event.persisted) window.location.reload();
  });
}

document.addEventListener("DOMContentLoaded", () => {
  const managerPanel = document.querySelector(".manager-side-panel");
  if (managerPanel) {
    const managerToggle = managerPanel.querySelector(".manager-panel-toggle");
    const managerLayout = managerPanel.closest(".manager-layout");
    const storageKey = "mtchlvr-manager-menu-collapsed-v2";
    const normalizePath = (path) => path.replace(/\/+$/, "") || "/";

    managerPanel.querySelectorAll(".manager-side-link").forEach((link) => {
      if (normalizePath(new URL(link.href, window.location.origin).pathname) ===
          normalizePath(window.location.pathname)) {
        link.setAttribute("aria-current", "page");
      }
    });

    const setManagerPanelCollapsed = (collapsed) => {
      managerPanel.classList.toggle("is-collapsed", collapsed);
      managerLayout?.classList.toggle("manager-layout--collapsed", collapsed);
      managerToggle?.setAttribute("aria-expanded", String(!collapsed));

      const label = collapsed ? "Expand management menu" : "Collapse management menu";
      managerToggle?.setAttribute("aria-label", label);
      managerToggle?.setAttribute("title", label);

      const visibleLabel = managerToggle?.querySelector(".manager-panel-toggle-label");
      if (visibleLabel) visibleLabel.textContent = collapsed ? "Expand" : "Collapse";
    };

    let startsCollapsed = true;
    try {
      startsCollapsed = window.localStorage.getItem(storageKey) === "true";
    } catch (_) {
      // The menu remains usable when browser storage is unavailable.
    }
    setManagerPanelCollapsed(startsCollapsed);

    managerToggle?.addEventListener("click", () => {
      const collapsed = !managerPanel.classList.contains("is-collapsed");
      setManagerPanelCollapsed(collapsed);
      try {
        window.localStorage.setItem(storageKey, String(collapsed));
      } catch (_) {
        // Keep the current-page toggle working when storage is unavailable.
      }
    });
  }

  const today = new Date();
  const localDate = new Date(today.getTime() - today.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  document.querySelectorAll('input[name="checkIn"]').forEach((input) => {
    input.min = localDate;
    input.addEventListener("change", () => {
      const out = input.closest("form")?.querySelector('input[name="checkOut"]');
      if (out) { out.min = input.value || localDate; if (out.value && out.value < input.value) out.value = ""; }
    });
  });
  document.querySelectorAll('input[name="checkOut"]').forEach((input) => input.min = localDate);

  const checkoutReminder = document.getElementById("checkout-day-reminder");
  const checkoutModal = document.getElementById("checkout-reminder-modal");

  if (checkoutReminder && checkoutModal) {
    const reminderText = checkoutReminder.textContent.replace(/\s+/g, " ").trim();
    const message = checkoutModal.querySelector("[data-checkout-message]");
    const dismissButtons = checkoutModal.querySelectorAll("[data-checkout-dismiss]");
    const closeCheckoutReminder = () => { checkoutModal.hidden = true; };

    dismissButtons.forEach((button) => button.addEventListener("click", closeCheckoutReminder));
    checkoutModal.addEventListener("click", (event) => {
      if (event.target === checkoutModal) closeCheckoutReminder();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !checkoutModal.hidden) closeCheckoutReminder();
    });

    // Show the reminder whenever the customer opens their reservations on checkout day.
    if (message) message.textContent = reminderText;
    checkoutModal.hidden = false;
    dismissButtons[0]?.focus();
  }

  document.querySelectorAll("[data-password-toggle]").forEach((button) => {
    button.addEventListener("click", () => {
      const input = document.getElementById(button.dataset.passwordToggle);
      if (!input) return;
      const visible = input.type === "password";
      input.type = visible ? "text" : "password";
      button.textContent = visible ? "Hide" : "Show";
      button.setAttribute("aria-label", visible ? "Hide password" : "Show password");
      button.setAttribute("aria-pressed", String(visible));
    });
  });

  const modal = document.getElementById("confirm-modal");
  if (modal) {
    const message = modal.querySelector("[data-confirm-message]");
    const cancel = modal.querySelector("[data-confirm-cancel]");
    const proceed = modal.querySelector("[data-confirm-proceed]");
    let pendingForm = null;
    document.querySelectorAll("form[data-confirm]").forEach(form => form.addEventListener("submit", event => {
      if (form.dataset.confirmed === "true") { form.dataset.confirmed = "false"; return; }
      event.preventDefault();
      pendingForm = form;
      message.textContent = form.dataset.confirm || "Are you sure you want to continue?";
      modal.hidden = false;
      cancel.focus();
    }));
    const close = () => { modal.hidden = true; pendingForm = null; };
    cancel.addEventListener("click", close);
    modal.addEventListener("click", event => { if (event.target === modal) close(); });
    proceed.addEventListener("click", () => {
      if (!pendingForm) return;
      const form = pendingForm;
      close();
      form.dataset.confirmed = "true";
      form.requestSubmit();
    });
    document.addEventListener("keydown", event => { if (event.key === "Escape" && !modal.hidden) close(); });
  }

  // Validate full reservation intervals and display active room occupancy.
  document.querySelectorAll('form.booking-form').forEach((form) => {
    const checkIn = form.querySelector('input[name="checkIn"]');
    const checkInTime = form.querySelector('input[name="checkInTime"]');
    const checkOut = form.querySelector('input[name="checkOut"]');
    const checkOutTime = form.querySelector('input[name="checkOutTime"]');

    if (!checkIn || !checkInTime || !checkOut || !checkOutTime) return;

    const validateInterval = () => {
      checkOut.min = checkIn.value || localDate;
      if (!checkIn.value || !checkOut.value) {
        checkOut.setCustomValidity("");
        return;
      }
      const start = `${checkIn.value}T${checkInTime.value || "14:00"}`;
      const end = `${checkOut.value}T${checkOutTime.value || "12:00"}`;
      checkOut.setCustomValidity(end > start ? "" :
        "Check-out must be later than check-in.");
    };

    [checkIn, checkInTime, checkOut, checkOutTime].forEach((input) => {
      input.addEventListener("change", validateInterval);
    });
    form.addEventListener("submit", (event) => {
      validateInterval();
      if (!form.checkValidity()) {
        event.preventDefault();
        form.reportValidity();
      }
    });
  });

  document.querySelectorAll(".room-availability[data-room-id]").forEach(async (panel) => {
    const message = panel.querySelector("[data-availability-message]");
    const list = panel.querySelector("[data-availability-list]");
    const card = panel.closest(".room-card");
    const openButton = card?.querySelector(".room-availability-toggle");
    const closeButton = panel.querySelector(".availability-popup-close");
    if (!message || !list) return;

    // Keep the original position so the popup can be restored after closing.
    const originalParent = panel.parentNode;
    const originalNextSibling = panel.nextSibling;
    let isOpen = false;

    const closePopup = (restoreFocus = false) => {
      if (!isOpen) return;

      panel.hidden = true;

      if (originalParent) {
        if (originalNextSibling && originalNextSibling.parentNode === originalParent) {
          originalParent.insertBefore(panel, originalNextSibling);
        } else {
          originalParent.appendChild(panel);
        }
      }

      isOpen = false;
      openButton?.setAttribute("aria-expanded", "false");
      document.body.classList.remove("availability-popup-open");

      if (restoreFocus) openButton?.focus();
    };

    openButton?.addEventListener("click", () => {
      // Move it out of the transformed room card to avoid hover/stacking issues.
      document.body.appendChild(panel);
      panel.hidden = false;
      isOpen = true;
      openButton.setAttribute("aria-expanded", "true");
      document.body.classList.add("availability-popup-open");
      closeButton?.focus();
    });

    closeButton?.addEventListener("click", () => closePopup(true));

    // Close when clicking outside the popup or pressing Escape.
    document.addEventListener("click", (event) => {
      if (
        isOpen &&
        !panel.contains(event.target) &&
        event.target !== openButton &&
        !openButton?.contains(event.target)
      ) {
        closePopup();
      }
    });

    document.addEventListener("keydown", (event) => {
      if (isOpen && event.key === "Escape") {
        closePopup(true);
      }
    });

    try {
      const response = await fetch(
        `/api/rooms/${encodeURIComponent(panel.dataset.roomId)}/availability`,
        { headers: { "Accept": "application/json" } }
      );
      if (!response.ok) throw new Error("Schedule could not be loaded.");

      const bookings = await response.json();
      list.replaceChildren();

      if (!bookings.length) {
        message.textContent = "No existing reservations. Choose your preferred dates and times.";
        return;
      }

      bookings.sort((a, b) =>
        `${a.checkIn}T${a.checkInTime || "14:00"}`.localeCompare(
          `${b.checkIn}T${b.checkInTime || "14:00"}`
        )
      );

      bookings.forEach((booking) => {
        const item = document.createElement("li");
        item.className = "availability-booking";

        const status = (booking.status || "PENDING").toUpperCase();
        const friendlyStatus = status === "APPROVED" ? "Reserved"
          : status === "CANCELLATION_REQUESTED" ? "Cancellation requested"
          : status === "PENDING" ? "Pending"
          : status.replaceAll("_", " ");

        const dateLine = document.createElement("div");
        dateLine.className = "availability-date";
        dateLine.textContent =
          `${booking.checkIn} · ${booking.checkInTime || "14:00"} – ` +
          `${booking.checkOut} · ${booking.checkOutTime || "12:00"}`;

        const statusLine = document.createElement("span");
        statusLine.className = "availability-status availability-status-" +
          status.toLowerCase().replaceAll("_", "-");
        statusLine.textContent = friendlyStatus;

        item.append(dateLine, statusLine);
        list.appendChild(item);
      });

      message.textContent = "Occupied dates and times";
    } catch (_) {
      message.textContent = "Room schedule is temporarily unavailable. Please try again later.";
    }
  });

});
