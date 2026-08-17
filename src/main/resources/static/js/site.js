(function () {
    "use strict";

    const init = () => {
        document.querySelectorAll("[data-current-year]").forEach((element) => {
            element.textContent = String(new Date().getFullYear());
        });

        document.querySelectorAll("form[data-confirm]").forEach((form) => {
            form.addEventListener("submit", (event) => {
                if (!window.confirm(form.dataset.confirm || "¿Querés continuar?")) {
                    event.preventDefault();
                }
            });
        });

        document.querySelectorAll("[data-menu-toggle][aria-controls]").forEach((button) => {
            const menu = document.getElementById(button.getAttribute("aria-controls"));
            if (!menu) return;

            const closeMenu = () => {
                menu.classList.remove("show");
                button.setAttribute("aria-expanded", "false");
                button.setAttribute("aria-label", "Abrir menú");
            };

            button.addEventListener("click", () => {
                const willOpen = !menu.classList.contains("show");
                menu.classList.toggle("show", willOpen);
                button.setAttribute("aria-expanded", String(willOpen));
                button.setAttribute("aria-label", willOpen ? "Cerrar menú" : "Abrir menú");
            });

            menu.querySelectorAll("a").forEach((link) => link.addEventListener("click", closeMenu));
            document.addEventListener("keydown", (event) => {
                if (event.key === "Escape" && menu.classList.contains("show")) {
                    closeMenu();
                    button.focus();
                }
            });
        });
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init, { once: true });
    } else {
        init();
    }
}());
