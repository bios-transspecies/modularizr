package com.decentralizer.spreadr.apigateway.security;

import com.decentralizer.spreadr.modules.appconfig.domain.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.decentralizer.spreadr.SpreadrApplication.INSTANCE_ID;

@Component
@Transactional
@Slf4j
@RequiredArgsConstructor
class SpringControllersForSecurity {

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String DELETE = "DELETE";
    private static final String PUT = "PUT";
    private static final String PATCH = "PATCH";
    private final SecurityAppConfigClient securityAppConfigClient;
    private final HashSet<AnnotatedController> controllers = new HashSet<>();

    @PostConstruct
    public void postConstruct() {
        List<Class> classes = findControllers(getScanner());
        addRequestMappingAnnotatedClassesToControllers(classes);
        addNewControllersToDatabase();
    }

    public Set<AnnotatedController> getControllers() {
        return controllers;
    }

    private void addNewControllersToDatabase() {
        controllers.stream()
                .map(getAnnotatedControllerActionFunction())
                .forEach(c -> securityAppConfigClient.addNewControllerToDatabase(c, INSTANCE_ID));
    }

    private Function<AnnotatedController, Controller> getAnnotatedControllerActionFunction() {
        return c -> {
            Controller controller = new Controller();
            controller.setController(c.getClassLevelAnnotation());
            controller.setMethod(c.getMethodLevelAnnotation());
            controller.setHttpMethod(c.getHttpMethod());
            return controller;
        };
    }

    private ClassPathScanningCandidateComponentProvider getScanner() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Controller.class));
        return scanner;
    }

    private void addRequestMappingAnnotatedClassesToControllers(List<Class> classes) {
        if (classes != null) {
            for (Class parsedClass : classes) {
                Annotation[] annotations = null;
                annotations = getAnnotations(parsedClass, annotations);
                if (annotations != null) {
                    for (Annotation annotation : annotations) {
                        if (annotation.annotationType() == RequestMapping.class) {
                            String[] path = ((RequestMapping) annotation).value();
                            String controller = Arrays.stream(path)
                                    .map(p -> "/" + p)
                                    .collect(Collectors.joining("/"));
                            controller = controller.replace("//", "/");
                            addAnnotatedMethods(parsedClass, controller);
                        }
                    }
                }
            }
        }
    }

    private void addAnnotatedMethods(Class parsedClass, String controller) {
        Annotation[] annotations;
        Method[] methods = parsedClass.getMethods();
        for (Method method : methods) {
            annotations = method.getDeclaredAnnotations();
            processAnnotations(controller, annotations);
        }
    }

    private void processAnnotations(String controller, Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (isMappingController(annotation)) {
                var iterator = getMappingValue(annotation).entrySet().iterator();
                while (iterator.hasNext()) {
                    addToControllers(controller, iterator);
                }
            }
        }
    }

    private void addToControllers(String controller, Iterator<Map.Entry<String[], String>> iterator) {
        Map.Entry<String[], String> pos = iterator.next();
        String mapping = Arrays.stream(pos.getKey())
                .map(p -> "/" + p)
                .collect(Collectors.joining("/"));
        mapping = removeDuplicatedSlashes(mapping);
        controllers.add(new AnnotatedController(controller, mapping, pos.getValue()));
    }

    private String removeDuplicatedSlashes(String mapping) {
        if (mapping.contains("//"))
            mapping = mapping.replace("//", "/");
        if (mapping.contains("//"))
            mapping = removeDuplicatedSlashes(mapping);
        mapping = mapping.replaceAll("\\{[^{}]*}", "*");
        return mapping;
    }

    private boolean isMappingController(Annotation annotation) {
        return annotation.annotationType() == GetMapping.class ||
                annotation.annotationType() == PostMapping.class ||
                annotation.annotationType() == DeleteMapping.class ||
                annotation.annotationType() == PutMapping.class ||
                annotation.annotationType() == PatchMapping.class ||
                annotation.annotationType() == RequestMapping.class;
    }

    private List<Class> findControllers(ClassPathScanningCandidateComponentProvider scanner) {
        List<Class> classes = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents("com.decentralizer.spreadr")) {
            try {
                classes.add(Class.forName(beanDefinition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                log.info("class not found: [{}]", e);
            }
        }
        return classes;
    }

    private Annotation[] getAnnotations(Class parsedClass, Annotation[] annotations) {
        try {
            annotations = parsedClass.getAnnotationsByType(Class.forName(RequestMapping.class.getCanonicalName()));
        } catch (ClassNotFoundException e) {
            log.error("error: [{}]", e);
        }
        return annotations;
    }


    private Map<String[], String> getMappingValue(Annotation annotation) {
        Map<String[], String> values = new HashMap<>();
        if (annotation.annotationType() == GetMapping.class) {
            GetMapping mapping = (GetMapping) annotation;
            values.put(mapping.value(), GET);
        }
        if (annotation.annotationType() == PostMapping.class) {
            PostMapping mapping = (PostMapping) annotation;
            values.put(mapping.value(), POST);
        }
        if (annotation.annotationType() == DeleteMapping.class) {
            DeleteMapping mapping = (DeleteMapping) annotation;
            values.put(mapping.value(), DELETE);
        }
        if (annotation.annotationType() == PutMapping.class) {
            PutMapping mapping = (PutMapping) annotation;
            values.put(mapping.value(), PUT);
        }
        if (annotation.annotationType() == PatchMapping.class) {
            PatchMapping mapping = (PatchMapping) annotation;
            values.put(mapping.value(), PATCH);
        }
        if (annotation.annotationType() == RequestMapping.class) {
            RequestMapping mapping = (RequestMapping) annotation;
            RequestMethod[] methods = mapping.method();
            Stream.of(methods).forEach(m -> values.put(mapping.value(), m.name()));
        }
        return values;
    }


    public static class AnnotatedController {
        private final String classLevelAnnotation;
        private final String methodLevelAnnotation;
        private final String httpMethod;

        AnnotatedController(String classLevelAnnotation, String methodLevelAnnotation, String httpMethod) {
            this.classLevelAnnotation = classLevelAnnotation;
            if (methodLevelAnnotation.equals("/"))
                methodLevelAnnotation = "";
            this.methodLevelAnnotation = methodLevelAnnotation;
            this.httpMethod = httpMethod;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public String getClassLevelAnnotation() {
            return classLevelAnnotation;
        }

        public String getMethodLevelAnnotation() {
            return methodLevelAnnotation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnnotatedController)) return false;
            AnnotatedController that = (AnnotatedController) o;
            return Objects.equals(getClassLevelAnnotation(), that.getClassLevelAnnotation()) &&
                    Objects.equals(getMethodLevelAnnotation(), that.getMethodLevelAnnotation()) &&
                    Objects.equals(getHttpMethod(), that.getHttpMethod());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClassLevelAnnotation(), getMethodLevelAnnotation(), getHttpMethod());
        }
    }

}
