(function () {
    "use strict";
    const id = new URLSearchParams(window.location.search).get("id");
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id || "") ||
        id === "00000000-0000-0000-0000-000000000000") return;
    const link = document.getElementById("event-hold-link");
    link.href = (document.body.dataset.context || "") + "/select-tickets?eventId=" + encodeURIComponent(id);
    link.hidden = false;
})();
