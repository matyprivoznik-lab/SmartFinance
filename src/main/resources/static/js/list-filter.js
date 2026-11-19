(function () {
    var form = document.getElementById("list-filter");
    if (!form) {
        return;
    }

    var rows = Array.prototype.slice.call(document.querySelectorAll("[data-search]"));
    var empty = document.getElementById("filter-empty");
    var count = document.getElementById("filter-count");

    function value(name) {
        var field = form.querySelector("[name='" + name + "']");
        return field ? field.value : "";
    }

    function fold(value) {
        return (value || "")
            .toString()
            .normalize("NFD")
            .replace(/\p{M}/gu, "")
            .toLowerCase()
            .replace(/\s+/g, " ")
            .trim();
    }

    function matches(haystack, query) {
        var foldedQuery = fold(query);
        if (!foldedQuery) {
            return true;
        }
        var foldedHay = fold(haystack);
        var compactHay = foldedHay.replace(/ /g, "");
        return foldedQuery.split(" ").every(function (part) {
            return !part || foldedHay.indexOf(part) !== -1 || compactHay.indexOf(part) !== -1;
        });
    }

    function apply() {
        var query = value("q");
        var kind = value("kind");
        var status = value("status");
        var ico = value("ico");
        var unit = value("unit");
        var shown = 0;

        rows.forEach(function (row) {
            var visible = matches(row.getAttribute("data-search"), query);
            if (kind && row.getAttribute("data-kind") !== kind) {
                visible = false;
            }
            if (status && row.getAttribute("data-status") !== status) {
                visible = false;
            }
            if (ico === "with" && row.getAttribute("data-ico") !== "1") {
                visible = false;
            }
            if (ico === "without" && row.getAttribute("data-ico") === "1") {
                visible = false;
            }
            if (unit && (row.getAttribute("data-unit") || "") !== unit) {
                visible = false;
            }
            row.classList.toggle("is-filtered-out", !visible);
            if (visible) {
                shown += 1;
            }
        });

        if (count) {
            count.textContent = String(shown);
        }
        if (empty) {
            empty.hidden = shown !== 0;
        }
    }

    form.addEventListener("input", apply);
    form.addEventListener("change", apply);
    apply();
})();
