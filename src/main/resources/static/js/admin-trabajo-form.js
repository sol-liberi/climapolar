(function () {
    "use strict";

    const init = () => {
        const form = document.getElementById("workForm");
        if (!form) {
            return;
        }

        const imageInput = document.getElementById("imagenes");
        const videoInput = document.getElementById("videos");
        const previewContainer = document.getElementById("newMediaPreview");
        const submitButton = document.getElementById("submitWork");
        const submitStatus = document.getElementById("submitStatus");
        const selections = {
            imagenes: imageInput ? Array.from(imageInput.files || []) : [],
            videos: videoInput ? Array.from(videoInput.files || []) : []
        };
        let objectUrls = [];
        const maxCounts = { imagenes: 20, videos: 6 };
        const maxTotalFiles = 24;
        // Dejamos margen para los encabezados multipart del límite servidor de 180 MB.
        const maxRequestBytes = 179 * 1024 * 1024;

        const inputFor = (inputId) => inputId === "imagenes" ? imageInput : videoInput;
        const fileKey = (file) => [file.name, file.size, file.lastModified, file.type].join("::");
        const existingCount = (inputId) => document.querySelectorAll(
            `.existing-media-item[data-media-type="${inputId}"]:not(.is-marked-for-removal)`
        ).length;
        const existingTotal = () => document.querySelectorAll(
            ".existing-media-item:not(.is-marked-for-removal)"
        ).length;

        const revokeObjectUrls = () => {
            objectUrls.forEach((url) => URL.revokeObjectURL(url));
            objectUrls = [];
        };

        const formatBytes = (bytes) => {
            if (!Number.isFinite(bytes) || bytes <= 0) {
                return "0 KB";
            }
            const units = ["B", "KB", "MB", "GB"];
            const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
            const value = bytes / (1024 ** unitIndex);
            return `${value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unitIndex]}`;
        };

        const supportsFileListEditing = (input) => {
            if (!input || typeof DataTransfer === "undefined") {
                return false;
            }
            try {
                const transfer = new DataTransfer();
                input.files = transfer.files;
                return true;
            } catch (_error) {
                return false;
            }
        };

        const editableFileLists = {
            imagenes: supportsFileListEditing(imageInput),
            videos: supportsFileListEditing(videoInput)
        };

        const syncInputFiles = (inputId) => {
            const input = inputFor(inputId);
            if (!input || !editableFileLists[inputId]) {
                return false;
            }

            try {
                const transfer = new DataTransfer();
                selections[inputId].forEach((file) => transfer.items.add(file));
                input.files = transfer.files;
                return true;
            } catch (_error) {
                return false;
            }
        };

        const createPreview = (file, inputId, index) => {
            const card = document.createElement("figure");
            card.className = "media-preview-card";

            const mediaWrap = document.createElement("div");
            mediaWrap.className = "media-preview-visual";
            const objectUrl = URL.createObjectURL(file);
            objectUrls.push(objectUrl);

            if (inputId === "videos") {
                const video = document.createElement("video");
                video.src = objectUrl;
                video.controls = true;
                video.preload = "metadata";
                video.playsInline = true;
                video.muted = true;
                video.setAttribute("aria-label", `Vista previa de ${file.name}`);
                mediaWrap.appendChild(video);
            } else {
                const image = document.createElement("img");
                image.src = objectUrl;
                image.alt = `Vista previa de ${file.name}`;
                mediaWrap.appendChild(image);
            }

            const caption = document.createElement("figcaption");
            caption.className = "media-preview-info";
            const name = document.createElement("span");
            name.className = "media-preview-name";
            name.textContent = file.name;
            const size = document.createElement("small");
            size.textContent = formatBytes(file.size);
            caption.append(name, size);

            card.append(mediaWrap, caption);
            if (editableFileLists[inputId]) {
                const removeButton = document.createElement("button");
                removeButton.type = "button";
                removeButton.className = "media-preview-remove";
                removeButton.textContent = "Quitar";
                removeButton.setAttribute("aria-label", `Quitar ${file.name}`);
                removeButton.addEventListener("click", () => {
                    const removed = selections[inputId].splice(index, 1)[0];
                    if (!syncInputFiles(inputId)) {
                        selections[inputId].splice(index, 0, removed);
                        editableFileLists[inputId] = false;
                        showUploadError(inputFor(inputId), "No pudimos modificar esta selección. Elegí los archivos nuevamente.");
                    }
                    renderPreviews();
                });
                card.appendChild(removeButton);
            }
            return card;
        };

        const renderPreviews = () => {
            if (!previewContainer) {
                return;
            }

            revokeObjectUrls();
            previewContainer.replaceChildren();
            selections.imagenes.forEach((file, index) => {
                previewContainer.appendChild(createPreview(file, "imagenes", index));
            });
            selections.videos.forEach((file, index) => {
                previewContainer.appendChild(createPreview(file, "videos", index));
            });
            previewContainer.hidden = selections.imagenes.length + selections.videos.length === 0;
        };

        const showUploadError = (input, message) => {
            const target = document.querySelector(`[data-upload-error-for="${input.id}"]`);
            if (target) {
                target.textContent = message;
                target.hidden = !message;
            }
        };

        const mergeFiles = (input, incomingFiles) => {
            const inputId = input.id;
            const allowedTypes = inputId === "imagenes"
                ? new Set(["image/jpeg", "image/png"])
                : new Set(["video/mp4", "video/webm"]);
            const allowedExtension = inputId === "imagenes" ? /\.(?:jpe?g|png)$/i : /\.(?:mp4|webm)$/i;
            const hasAllowedFormat = (file) => allowedTypes.has(file.type.toLowerCase())
                || (!file.type && allowedExtension.test(file.name));
            const maxBytes = Number.parseInt(input.dataset.maxBytes || "0", 10) || 0;
            const canMerge = editableFileLists[inputId];
            const nextSelection = canMerge ? Array.from(selections[inputId]) : [];
            const currentKeys = new Set(nextSelection.map(fileKey));
            const rejected = [];

            if (!canMerge) {
                const replacement = Array.from(incomingFiles);
                const otherSelection = selections[inputId === "imagenes" ? "videos" : "imagenes"];
                replacement.forEach((file) => {
                    if (!hasAllowedFormat(file)) {
                        rejected.push(`${file.name}: formato no admitido`);
                    } else if (maxBytes > 0 && file.size > maxBytes) {
                        rejected.push(`${file.name}: supera ${formatBytes(maxBytes)}`);
                    }
                });
                if (replacement.length + existingCount(inputId) > maxCounts[inputId]) {
                    rejected.push(`Seleccioná como máximo ${maxCounts[inputId]} archivos de este tipo`);
                }
                if (replacement.length + otherSelection.length + existingTotal() > maxTotalFiles) {
                    rejected.push(`La publicación admite hasta ${maxTotalFiles} archivos en total`);
                }
                const totalBytes = [...replacement, ...otherSelection].reduce((sum, file) => sum + file.size, 0);
                if (totalBytes > maxRequestBytes) {
                    rejected.push("El envío completo debe mantenerse debajo de 180 MB");
                }
                if (rejected.length > 0) {
                    input.value = "";
                    selections[inputId] = [];
                    rejected.push("Volvé a elegir los archivos válidos.");
                    showUploadError(input, rejected.join(" · "));
                } else {
                    selections[inputId] = replacement;
                    showUploadError(input, "En este navegador, una nueva selección reemplaza la anterior. Para elegir varios archivos, seleccionalos juntos.");
                }
                renderPreviews();
                return;
            }

            Array.from(incomingFiles).forEach((file) => {
                if (!hasAllowedFormat(file)) {
                    rejected.push(`${file.name}: formato no admitido`);
                    return;
                }
                if (maxBytes > 0 && file.size > maxBytes) {
                    rejected.push(`${file.name}: supera ${formatBytes(maxBytes)}`);
                    return;
                }
                const key = fileKey(file);
                if (!currentKeys.has(key)) {
                    const totalFiles = nextSelection.length
                        + selections[inputId === "imagenes" ? "videos" : "imagenes"].length
                        + existingTotal();
                    const totalBytes = [...nextSelection, ...selections[inputId === "imagenes" ? "videos" : "imagenes"]]
                        .reduce((sum, selectedFile) => sum + selectedFile.size, 0);
                    if (nextSelection.length + existingCount(inputId) >= maxCounts[inputId]) {
                        rejected.push(`${file.name}: se alcanzó el máximo de ${maxCounts[inputId]} archivos`);
                        return;
                    }
                    if (totalFiles >= maxTotalFiles) {
                        rejected.push(`${file.name}: se alcanzó el máximo total de ${maxTotalFiles} archivos`);
                        return;
                    }
                    if (totalBytes + file.size > maxRequestBytes) {
                        rejected.push(`${file.name}: el envío completo superaría el límite de 180 MB`);
                        return;
                    }
                    nextSelection.push(file);
                    currentKeys.add(key);
                }
            });

            selections[inputId] = nextSelection;
            if (!syncInputFiles(inputId)) {
                editableFileLists[inputId] = false;
                selections[inputId] = Array.from(input.files || []);
                showUploadError(input, "Tu navegador no pudo combinar selecciones. Conservamos únicamente los archivos que aparecen en el selector.");
                renderPreviews();
                return;
            }
            showUploadError(input, rejected.join(" · "));
            renderPreviews();
        };

        [imageInput, videoInput].filter(Boolean).forEach((input) => {
            input.addEventListener("change", () => {
                const incomingFiles = Array.from(input.files || []);
                mergeFiles(input, incomingFiles);
            });
        });

        document.querySelectorAll(".upload-zone[data-input-id]").forEach((zone) => {
            const input = document.getElementById(zone.dataset.inputId);
            if (!input) {
                return;
            }

            ["dragenter", "dragover"].forEach((eventName) => {
                zone.addEventListener(eventName, (event) => {
                    event.preventDefault();
                    zone.classList.add("is-dragging");
                });
            });
            ["dragleave", "drop"].forEach((eventName) => {
                zone.addEventListener(eventName, (event) => {
                    event.preventDefault();
                    zone.classList.remove("is-dragging");
                });
            });
            zone.addEventListener("drop", (event) => {
                if (event.dataTransfer && event.dataTransfer.files.length > 0) {
                    if (editableFileLists[input.id]) {
                        mergeFiles(input, event.dataTransfer.files);
                    } else {
                        showUploadError(input, "Este navegador no permite arrastrar archivos. Usá el botón Elegir archivos.");
                    }
                }
            });
        });

        const updateExistingMediaButton = (button, checkbox) => {
            const removed = checkbox.checked;
            const item = button.closest(".existing-media-item");
            if (item) {
                item.classList.toggle("is-marked-for-removal", removed);
            }
            button.setAttribute("aria-pressed", String(removed));

            const label = button.querySelector("[data-action-label]");
            const removeLabel = button.dataset.removeLabel || "Eliminar";
            const restoreLabel = button.dataset.restoreLabel || "Deshacer";
            if (label) {
                label.textContent = removed ? restoreLabel : removeLabel;
            } else {
                button.textContent = removed ? restoreLabel : removeLabel;
            }
        };

        document.querySelectorAll(".remove-existing-media[data-checkbox-id]").forEach((button) => {
            const checkbox = document.getElementById(button.dataset.checkboxId);
            if (!checkbox) {
                return;
            }
            updateExistingMediaButton(button, checkbox);
            button.addEventListener("click", (event) => {
                event.preventDefault();
                checkbox.checked = !checkbox.checked;
                updateExistingMediaButton(button, checkbox);
            });
        });

        const titleInput = document.querySelector("#titulo, [name='titulo']");
        const categoryInput = document.querySelector("#categoria, #tipo, [name='categoria'], [name='categoriaCodigo'], [name='tipoServicio']");
        const descriptionInput = document.querySelector("#descripcion, [name='descripcion']");
        const dateInput = document.querySelector("#fecha, [name='fecha']");
        const previewTitle = document.getElementById("previewTitle");
        const previewCategory = document.getElementById("previewCategory");
        const previewDescription = document.getElementById("previewDescription");
        const previewDate = document.getElementById("previewDate");
        const dateFormatter = new Intl.DateTimeFormat("es-AR", {
            day: "2-digit",
            month: "2-digit",
            year: "numeric"
        });

        const selectedText = (select) => {
            const option = select && select.selectedOptions && select.selectedOptions[0];
            return option && option.value ? option.textContent.trim() : "Sin categoría";
        };

        const formattedDate = (value) => {
            const parts = String(value || "").split("-").map(Number);
            if (parts.length !== 3 || parts.some((part) => !Number.isFinite(part))) {
                return "Fecha sin definir";
            }
            return dateFormatter.format(new Date(parts[0], parts[1] - 1, parts[2]));
        };

        const updatePublicationPreview = () => {
            if (previewTitle) {
                previewTitle.textContent = titleInput && titleInput.value.trim()
                    ? titleInput.value.trim()
                    : "Título del trabajo";
            }
            if (previewCategory) {
                previewCategory.textContent = categoryInput instanceof HTMLSelectElement
                    ? selectedText(categoryInput)
                    : (categoryInput && categoryInput.value.trim() ? categoryInput.value.trim() : "Sin categoría");
            }
            if (previewDescription) {
                previewDescription.textContent = descriptionInput && descriptionInput.value.trim()
                    ? descriptionInput.value.trim()
                    : "La descripción del trabajo aparecerá acá.";
            }
            if (previewDate) {
                previewDate.textContent = formattedDate(dateInput && dateInput.value);
            }
        };

        [titleInput, categoryInput, descriptionInput, dateInput].filter(Boolean).forEach((field) => {
            field.addEventListener(field instanceof HTMLSelectElement ? "change" : "input", updatePublicationPreview);
        });

        const resetSubmitState = () => {
            delete form.dataset.submitting;
            if (submitButton) {
                submitButton.disabled = false;
                submitButton.textContent = submitButton.dataset.defaultLabel || "Guardar";
            }
            if (submitStatus) submitStatus.textContent = "";
        };

        form.addEventListener("submit", (event) => {
            if (form.dataset.submitting === "true") {
                event.preventDefault();
                return;
            }
            const imageCount = selections.imagenes.length + existingCount("imagenes");
            const videoCount = selections.videos.length + existingCount("videos");
            const totalCount = imageCount + videoCount;
            if (totalCount === 0 || imageCount > maxCounts.imagenes
                || videoCount > maxCounts.videos || totalCount > maxTotalFiles) {
                event.preventDefault();
                const message = totalCount === 0
                    ? "Agregá al menos una fotografía o un video antes de publicar."
                    : "Revisá la cantidad de archivos: se admiten 20 fotos, 6 videos y 24 archivos en total.";
                showUploadError(imageInput || videoInput, message);
                return;
            }
            const imagesSynced = !imageInput || !editableFileLists.imagenes || syncInputFiles("imagenes");
            const videosSynced = !videoInput || !editableFileLists.videos || syncInputFiles("videos");
            if (!imagesSynced || !videosSynced) {
                event.preventDefault();
                const failedInput = !imagesSynced ? imageInput : videoInput;
                showUploadError(failedInput, "No pudimos preparar los archivos. Elegilos nuevamente antes de publicar.");
                return;
            }
            form.dataset.submitting = "true";
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = submitButton.dataset.pendingLabel || "Publicando…";
            }
            if (submitStatus) submitStatus.textContent = "Publicando el trabajo. No cierres esta página.";
        });

        window.addEventListener("beforeunload", revokeObjectUrls, { once: true });
        window.addEventListener("pageshow", resetSubmitState);
        renderPreviews();
        updatePublicationPreview();
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init, { once: true });
    } else {
        init();
    }
}());
