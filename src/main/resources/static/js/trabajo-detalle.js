(function () {
    "use strict";

    const init = () => {
        const dialog = document.getElementById("mediaLightbox");
        const lightboxImage = document.getElementById("lightboxImage");
        const triggers = Array.from(document.querySelectorAll(".lightbox-trigger"));
        let lastTrigger = null;

        const safeImageUrl = (value) => {
            if (!value) {
                return "";
            }
            try {
                const url = new URL(value, document.baseURI);
                return url.protocol === "http:" || url.protocol === "https:" ? url.href : "";
            } catch (_error) {
                return "";
            }
        };

        const closeDialog = () => {
            if (!dialog) {
                return;
            }
            if (typeof dialog.close === "function" && dialog.open) {
                dialog.close();
            } else {
                dialog.removeAttribute("open");
            }
        };

        if (dialog && lightboxImage) {
            triggers.forEach((trigger) => {
                trigger.addEventListener("click", (event) => {
                    const preview = trigger.matches("img") ? trigger : trigger.querySelector("img");
                    const source = safeImageUrl(
                        trigger.dataset.fullSrc
                        || trigger.dataset.lightboxSrc
                        || trigger.getAttribute("href")
                        || (preview && (preview.currentSrc || preview.src))
                    );

                    if (!source) {
                        return;
                    }

                    event.preventDefault();
                    lastTrigger = trigger;
                    lightboxImage.src = source;
                    lightboxImage.alt = trigger.dataset.alt || (preview && preview.alt) || "Trabajo realizado por CLIMA POLAR";

                    const caption = document.getElementById("lightboxCaption");
                    if (caption) {
                        caption.textContent = trigger.dataset.caption || lightboxImage.alt;
                    }

                    if (typeof dialog.showModal === "function") {
                        dialog.showModal();
                    } else {
                        dialog.setAttribute("open", "");
                    }
                });
            });

            dialog.querySelectorAll("[data-lightbox-close]").forEach((button) => {
                button.addEventListener("click", closeDialog);
            });

            dialog.addEventListener("click", (event) => {
                if (event.target === dialog) {
                    closeDialog();
                }
            });

            dialog.addEventListener("close", () => {
                lightboxImage.removeAttribute("src");
                if (lastTrigger) {
                    lastTrigger.focus({ preventScroll: true });
                }
            });
        }

        const videos = Array.from(document.querySelectorAll(".work-gallery video, .detail-video"));
        videos.forEach((video) => {
            video.autoplay = false;
            video.playsInline = true;
            if (!video.hasAttribute("preload")) {
                video.preload = "metadata";
            }
            video.addEventListener("play", () => {
                videos.forEach((otherVideo) => {
                    if (otherVideo !== video) {
                        otherVideo.pause();
                    }
                });
            });
        });
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init, { once: true });
    } else {
        init();
    }
}());
