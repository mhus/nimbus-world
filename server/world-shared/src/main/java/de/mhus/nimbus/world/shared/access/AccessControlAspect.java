package de.mhus.nimbus.world.shared.access;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Aspect for processing access control annotations on REST endpoints.
 *
 * This aspect intercepts REST controller methods annotated with:
 * - @RequireWorldRole: Requires specific world role
 * - @RequireSectorRole: Requires specific sector role
 * - @RequireAgent: Requires agent authentication
 * - @RequireSession: Requires session authentication
 * - @RequireWorldIsNotInstance: Requires world to not be an instance
 *
 * If access is denied, returns HTTP 403 Forbidden.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessControlAspect {

    private final AccessValidator accessUtil;

    /**
     * Intercepts all REST controller methods to check access annotations.
     */
    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public Object checkAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Get current HTTP request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("No request attributes found - skipping access control");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();

        // Check class-level annotations first
        Class<?> declaringClass = method.getDeclaringClass();
        String accessDeniedReason = checkClassAnnotations(declaringClass, request);

        // Check method-level annotations (override class-level)
        if (accessDeniedReason == null) {
            accessDeniedReason = checkMethodAnnotations(method, request);
        }

        // If access denied, return 403 Forbidden
        if (accessDeniedReason != null) {
            log.warn("Access denied: {} - method: {}.{}",
                    accessDeniedReason,
                    method.getDeclaringClass().getSimpleName(),
                    method.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AccessDeniedResponse(accessDeniedReason));
        }

        // Access granted - proceed with method execution
        return joinPoint.proceed();
    }

    /**
     * Checks class-level annotations.
     */
    private String checkClassAnnotations(Class<?> clazz, HttpServletRequest request) {
        if (clazz.isAnnotationPresent(RequireWorldRole.class)) {
            RequireWorldRole annotation = clazz.getAnnotation(RequireWorldRole.class);
            if (!accessUtil.hasWorldRole(request, annotation.value())) {
                return "Missing required world role: " + annotation.value();
            }
        }

        if (clazz.isAnnotationPresent(RequireSectorRole.class)) {
            RequireSectorRole annotation = clazz.getAnnotation(RequireSectorRole.class);
            if (!accessUtil.hasSectorRole(request, annotation.value())) {
                return "Missing required sector role: " + annotation.value();
            }
        }

        if (clazz.isAnnotationPresent(RequireAgent.class)) {
            if (!accessUtil.isAgent(request)) {
                return "Agent authentication required";
            }
        }

        if (clazz.isAnnotationPresent(RequireSession.class)) {
            if (!accessUtil.hasSession(request)) {
                return "Session authentication required";
            }
        }

        if (clazz.isAnnotationPresent(RequireWorldIsNotInstance.class)) {
            if (accessUtil.isWorldInstance(request)) {
                return "Operation not allowed on instance worlds";
            }
        }

        return null; // Access granted
    }

    /**
     * Checks method-level annotations.
     */
    private String checkMethodAnnotations(Method method, HttpServletRequest request) {
        if (method.isAnnotationPresent(RequireWorldRole.class)) {
            RequireWorldRole annotation = method.getAnnotation(RequireWorldRole.class);
            if (!accessUtil.hasWorldRole(request, annotation.value())) {
                return "Missing required world role: " + annotation.value();
            }
        }

        if (method.isAnnotationPresent(RequireSectorRole.class)) {
            RequireSectorRole annotation = method.getAnnotation(RequireSectorRole.class);
            if (!accessUtil.hasSectorRole(request, annotation.value())) {
                return "Missing required sector role: " + annotation.value();
            }
        }

        if (method.isAnnotationPresent(RequireAgent.class)) {
            if (!accessUtil.isAgent(request)) {
                return "Agent authentication required";
            }
        }

        if (method.isAnnotationPresent(RequireSession.class)) {
            if (!accessUtil.hasSession(request)) {
                return "Session authentication required";
            }
        }

        if (method.isAnnotationPresent(RequireWorldIsNotInstance.class)) {
            if (accessUtil.isWorldInstance(request)) {
                return "Operation not allowed on instance worlds";
            }
        }

        return null; // Access granted
    }

    /**
     * DTO for access denied response.
     */
    private record AccessDeniedResponse(String reason) {}
}
