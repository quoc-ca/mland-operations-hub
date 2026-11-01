package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingQuoteController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopPackageController;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WorkshopDiscoveryBoundaryIT {

    private static final String BOOKING_CONTROLLERS =
            "edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller";

    @Test
    void packageAndSessionDiscoveryRoutesBelongToWorkshopOperations() throws Exception {
        Set<String> bookingRoutes = restControllerTypes(BOOKING_CONTROLLERS).stream()
                .flatMap(WorkshopDiscoveryBoundaryIT::routes)
                .collect(Collectors.toSet());
        Set<String> operationsRoutes = routes(WorkshopPackageController.class)
                .collect(Collectors.toSet());

        assertThat(bookingRoutes).doesNotContain(
                "/api/v1/workshop-packages",
                "/api/v1/workshop-packages/{packageId}/sessions");
        assertThat(operationsRoutes).contains(
                "/api/v1/workshop-packages",
                "/api/v1/workshop-packages/{packageId}/sessions");
        assertThat(restControllerTypes(BOOKING_CONTROLLERS))
                .contains(WorkshopBookingQuoteController.class);
    }

    @Test
    void bookingHasNoDirectDependencyOnOperationsPersistenceOrServices() {
        var classes = new ClassFileImporter()
                .importPackages("edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking");

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.workshopoperations.controller..",
                        "..features.workshopoperations.dao..",
                        "..features.workshopoperations.entity..",
                        "..features.workshopoperations.service..")
                .check(classes);
    }

    private static Set<Class<?>> restControllerTypes(String packageName) throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<Class<?>> result = new LinkedHashSet<>();
        for (var candidate : scanner.findCandidateComponents(packageName)) {
            result.add(Class.forName(candidate.getBeanClassName()));
        }
        return result;
    }

    private static Stream<String> routes(Class<?> controller) {
        RequestMapping controllerMapping = controller.getAnnotation(RequestMapping.class);
        String[] basePaths = controllerMapping == null
                ? new String[]{""}
                : mappingPaths(controllerMapping.value(), controllerMapping.path());
        return Arrays.stream(controller.getDeclaredMethods())
                .flatMap(method -> methodPaths(method).flatMap(path -> Arrays.stream(basePaths)
                        .map(base -> join(base, path))));
    }

    private static Stream<String> methodPaths(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) return Arrays.stream(mappingPaths(get.value(), get.path()));
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) return Arrays.stream(mappingPaths(post.value(), post.path()));
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) return Arrays.stream(mappingPaths(put.value(), put.path()));
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) return Arrays.stream(mappingPaths(patch.value(), patch.path()));
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) return Arrays.stream(mappingPaths(delete.value(), delete.path()));
        RequestMapping request = method.getAnnotation(RequestMapping.class);
        return request == null
                ? Stream.empty()
                : Arrays.stream(mappingPaths(request.value(), request.path()));
    }

    private static String[] mappingPaths(String[] value, String[] path) {
        return value.length == 0 ? path : value;
    }

    private static String join(String base, String path) {
        return (base + "/" + path).replaceAll("/+/", "/");
    }
}
