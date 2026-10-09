package clinic.exception;

import clinic.dto.ErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@ControllerAdvice
public class ApiExceptionHandler {

    private Object error(int status, String message, HttpServletRequest request) {
        ErrorDto error = new ErrorDto(
                Instant.now(),
                status,
                HttpStatus.valueOf(status).getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().equals("/slots"))
            return ResponseEntity.status(status).body(error);
        var view = new ModelAndView("error");
        view.setStatus(HttpStatus.valueOf(status));
        view.addObject("status", status); view.addObject("message", message);
        return view;
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public Object invalid(IllegalArgumentException e, HttpServletRequest r) { return error(400, e.getMessage(), r); }
    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public Object input(Exception e, HttpServletRequest r) { return error(400, "Check the request fields and their format", r); }
    @ExceptionHandler(ResponseStatusException.class)
    public Object application(ResponseStatusException e, HttpServletRequest r) { return error(e.getStatusCode().value(), e.getReason(), r); }
    @ExceptionHandler(Exception.class)
    public Object unexpected(Exception e, HttpServletRequest r) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Request failed: {}", r.getRequestURI(), e);
        return error(500, "Something went wrong. Please try again", r);
    }
}
