package com.friopolar.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice(assignableTypes = AdminTrabajoController.class)
public class AdminExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoDemasiadoGrande(MaxUploadSizeExceededException ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        model.addAttribute(
                "mensaje",
                "Un archivo o el envío completo supera el límite: 15 MB por foto, 150 MB por video y 180 MB en total."
        );
        return "admin/upload-error";
    }
}
