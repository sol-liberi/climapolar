(function () {
    "use strict";

    const whenReady = (callback) => {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", callback, { once: true });
        } else {
            callback();
        }
    };

    whenReady(() => {
        const section = document.getElementById("worksFeedSection");
        const feed = document.getElementById("worksFeed");
        if (!section || !feed) return;

        const apiUrl = section.dataset.apiUrl;
        const detailBaseUrl = section.dataset.detailBaseUrl || "/trabajos";
        const pageSize = Math.max(1, Number.parseInt(section.dataset.limit || "9", 10) || 9);
        const featured = section.dataset.featured;
        const loadingState = document.getElementById("worksLoading");
        const errorState = document.getElementById("worksError");
        const emptyState = document.getElementById("worksEmpty");
        const loadMoreButton = document.getElementById("loadMoreWorks");
        const retryButtons = Array.from(section.querySelectorAll("[data-works-retry]"));
        const filterLinks = Array.from(section.querySelectorAll(".work-filter[data-category]"));
        const dateFormatter = new Intl.DateTimeFormat("es-AR", { day: "2-digit", month: "2-digit", year: "numeric" });
        const parsedInitialPage = Number.parseInt(section.dataset.initialPage || "-1", 10);
        const hasServerPayload = Number.isFinite(parsedInitialPage) && parsedInitialPage >= 0;
        let currentCategory = section.dataset.initialCategory || "";
        let currentPage = hasServerPayload ? parsedInitialPage : -1;
        let totalPages = hasServerPayload ? Math.max(0, Number.parseInt(section.dataset.totalPages || "0", 10) || 0) : 1;
        let activeRequest = null;
        let loading = false;
        let failedRequestWasReset = false;

        const setHidden = (element, hidden) => {
            if (element) element.hidden = hidden;
        };

        const setLoading = (value, reset) => {
            loading = value;
            section.setAttribute("aria-busy", String(value));
            setHidden(loadingState, !value || feed.childElementCount > 0);
            if (loadMoreButton) {
                if (!loadMoreButton.dataset.defaultLabel) {
                    loadMoreButton.dataset.defaultLabel = loadMoreButton.textContent.trim() || "Cargar más trabajos";
                }
                loadMoreButton.disabled = value;
                loadMoreButton.textContent = value && !reset ? "Cargando…" : loadMoreButton.dataset.defaultLabel;
            }
            filterLinks.forEach((link) => link.setAttribute("aria-disabled", String(value)));
        };

        const resourceUrl = (value) => {
            if (!value || typeof value !== "string") return "";
            try {
                const parsed = new URL(value, document.baseURI);
                return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : "";
            } catch (_error) {
                return "";
            }
        };

        const detailUrl = (id) => {
            const base = detailBaseUrl.endsWith("/") ? detailBaseUrl.slice(0, -1) : detailBaseUrl;
            return `${base}/${encodeURIComponent(String(id))}`;
        };

        const formatDate = (isoDate) => {
            if (!isoDate || typeof isoDate !== "string") return "";
            const parts = isoDate.split("-").map(Number);
            if (parts.length !== 3 || parts.some((part) => !Number.isFinite(part))) return isoDate;
            return dateFormatter.format(new Date(parts[0], parts[1] - 1, parts[2]));
        };

        const mediaSummary = (images, videos) => {
            const labels = [];
            if (images > 0) labels.push(`${images} ${images === 1 ? "foto" : "fotos"}`);
            if (videos > 0) labels.push(`${videos} ${videos === 1 ? "video" : "videos"}`);
            return labels.join(" · ");
        };

        const appendText = (parent, tagName, className, value) => {
            const element = document.createElement(tagName);
            if (className) element.className = className;
            element.textContent = value;
            parent.appendChild(element);
            return element;
        };

        const createMedia = (trabajo, url) => {
            const wrapper = document.createElement("div");
            wrapper.className = "work-media";
            const cover = trabajo.portada || {};
            const type = String(cover.tipo || "").toLowerCase();
            const fullUrl = resourceUrl(cover.url);
            const thumbnailUrl = resourceUrl(cover.miniaturaUrl);

            if (type === "video" && fullUrl) {
                const link = document.createElement("a");
                link.className = "work-video-cover";
                link.href = url;
                link.setAttribute("aria-label", `Ver video y detalles de ${trabajo.titulo || "trabajo realizado"}`);
                appendText(link, "span", "work-video-play", "▶").setAttribute("aria-hidden", "true");
                appendText(link, "span", "", "Ver video");
                wrapper.appendChild(link);
            } else if (thumbnailUrl || fullUrl) {
                const link = document.createElement("a");
                link.className = "work-media-link";
                link.href = url;
                link.tabIndex = -1;
                link.setAttribute("aria-hidden", "true");
                const image = document.createElement("img");
                image.src = thumbnailUrl || fullUrl;
                image.alt = trabajo.titulo || "Trabajo realizado por CLIMA POLAR";
                image.loading = "lazy";
                image.decoding = "async";
                link.appendChild(image);
                wrapper.appendChild(link);
            } else {
                const fallback = document.createElement("div");
                fallback.className = "work-media-fallback";
                fallback.setAttribute("aria-hidden", "true");
                appendText(fallback, "span", "work-media-fallback-icon", "▧");
                appendText(fallback, "span", "", "Trabajo realizado");
                wrapper.appendChild(fallback);
            }

            const imageCount = Number(trabajo.cantidadImagenes) || 0;
            const videoCount = Number(trabajo.cantidadVideos) || 0;
            if (imageCount + videoCount > 1) {
                const count = appendText(wrapper, "span", "media-count", mediaSummary(imageCount, videoCount));
                count.setAttribute("aria-label", `Contenido: ${count.textContent}`);
            }
            return wrapper;
        };

        const createCard = (trabajo) => {
            const card = document.createElement("article");
            card.className = "work-card work-feed-card";
            card.dataset.workId = String(trabajo.id);
            const url = detailUrl(trabajo.id);
            card.appendChild(createMedia(trabajo, url));
            const body = document.createElement("div");
            body.className = "work-body";
            const meta = document.createElement("div");
            meta.className = "work-card-meta";
            const categoryLabel = trabajo.categoria && typeof trabajo.categoria === "object" ? trabajo.categoria.etiqueta : trabajo.categoria;
            if (categoryLabel) appendText(meta, "span", "tag", categoryLabel);
            if (trabajo.fecha) {
                const time = appendText(meta, "time", "", formatDate(trabajo.fecha));
                time.dateTime = trabajo.fecha;
            }
            body.appendChild(meta);
            const title = document.createElement("h3");
            title.className = "work-title";
            const titleLink = document.createElement("a");
            titleLink.href = url;
            titleLink.textContent = trabajo.titulo || "Trabajo realizado";
            title.appendChild(titleLink);
            body.appendChild(title);
            if (trabajo.descripcion) appendText(body, "p", "work-summary", trabajo.descripcion);
            const detailLink = document.createElement("a");
            detailLink.className = "work-detail-link";
            detailLink.href = url;
            detailLink.append("Ver trabajo ");
            appendText(detailLink, "span", "", "→").setAttribute("aria-hidden", "true");
            body.appendChild(detailLink);
            card.appendChild(body);
            return card;
        };

        const buildRequestUrl = (page) => {
            const url = new URL(apiUrl, document.baseURI);
            url.searchParams.set("page", String(page));
            url.searchParams.set("size", String(pageSize));
            if (currentCategory) url.searchParams.set("categoria", currentCategory);
            if (featured !== undefined && featured !== "") url.searchParams.set("destacado", featured);
            return url;
        };

        const updateLoadMore = () => {
            if (loadMoreButton) loadMoreButton.hidden = currentPage < 0 || currentPage + 1 >= totalPages;
        };

        const updateFilters = () => {
            filterLinks.forEach((link) => {
                const active = (link.dataset.category || "") === currentCategory;
                link.classList.toggle("is-active", active);
                if (active) link.setAttribute("aria-current", "page");
                else link.removeAttribute("aria-current");
            });
        };

        const updateBrowserUrl = () => {
            if (filterLinks.length === 0 || !window.history || typeof window.history.pushState !== "function") return;
            const url = new URL(window.location.href);
            url.searchParams.delete("page");
            if (currentCategory) url.searchParams.set("categoria", currentCategory);
            else url.searchParams.delete("categoria");
            window.history.pushState({ categoria: currentCategory }, "", url);
        };

        const loadWorks = async ({ reset = false } = {}) => {
            if (!apiUrl || (loading && !reset)) return;
            if (activeRequest) activeRequest.abort();
            const controller = new AbortController();
            activeRequest = controller;
            const requestedPage = reset ? 0 : currentPage + 1;
            setHidden(errorState, true);
            setHidden(emptyState, true);
            setLoading(true, reset);

            try {
                const response = await fetch(buildRequestUrl(requestedPage), {
                    headers: { Accept: "application/json" },
                    signal: controller.signal,
                    credentials: "same-origin"
                });
                if (!response.ok) throw new Error(`La API respondió con estado ${response.status}`);
                const payload = await response.json();
                const works = Array.isArray(payload.content) ? payload.content : [];
                const fragment = document.createDocumentFragment();
                const existingIds = reset ? new Set() : new Set(Array.from(feed.querySelectorAll("[data-work-id]"), (card) => card.dataset.workId));
                works.forEach((trabajo) => {
                    const id = String(trabajo.id);
                    if (!existingIds.has(id)) {
                        fragment.appendChild(createCard(trabajo));
                        existingIds.add(id);
                    }
                });
                if (reset) feed.replaceChildren(fragment);
                else feed.appendChild(fragment);
                currentPage = Number.isFinite(Number(payload.page)) ? Number(payload.page) : requestedPage;
                totalPages = Math.max(0, Number(payload.totalPages) || 0);
                const totalElements = Number(payload.totalElements);
                const isEmpty = currentPage === 0 && (Number.isFinite(totalElements) ? totalElements === 0 : works.length === 0);
                setHidden(emptyState, !isEmpty);
                failedRequestWasReset = false;
                updateLoadMore();
            } catch (error) {
                if (error.name === "AbortError") return;
                failedRequestWasReset = reset;
                setHidden(errorState, false);
                if (feed.childElementCount === 0) setHidden(emptyState, true);
                updateLoadMore();
            } finally {
                if (activeRequest === controller) {
                    activeRequest = null;
                    setLoading(false, reset);
                }
            }
        };

        filterLinks.forEach((link) => {
            link.addEventListener("click", (event) => {
                if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
                event.preventDefault();
                const nextCategory = link.dataset.category || "";
                if (nextCategory === currentCategory && currentPage >= 0) return;
                currentCategory = nextCategory;
                updateFilters();
                updateBrowserUrl();
                loadWorks({ reset: true });
            });
        });
        retryButtons.forEach((button) => button.addEventListener("click", () => loadWorks({ reset: failedRequestWasReset })));
        if (loadMoreButton) loadMoreButton.addEventListener("click", () => loadWorks());
        window.addEventListener("popstate", () => {
            const category = new URL(window.location.href).searchParams.get("categoria") || "";
            if (category !== currentCategory) {
                currentCategory = category;
                updateFilters();
                loadWorks({ reset: true });
            }
        });

        updateFilters();
        updateLoadMore();
        if (!hasServerPayload) loadWorks({ reset: true });
        else setHidden(loadingState, true);
    });
}());
