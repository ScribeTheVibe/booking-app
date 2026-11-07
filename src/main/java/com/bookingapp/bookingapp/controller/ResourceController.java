package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.AdminResourceResponse;
import com.bookingapp.bookingapp.dto.CreateResourceRequest;
import com.bookingapp.bookingapp.dto.UpdateResourceRequest;
import com.bookingapp.bookingapp.entity.BookableResource;
import com.bookingapp.bookingapp.entity.ResourceStatusFilter;
import com.bookingapp.bookingapp.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/{id}")
    public AdminResourceResponse getResource(@PathVariable Long id) {
        return AdminResourceResponse.from(resourceService.getResourceById(id));
    }

    @GetMapping
    public List<AdminResourceResponse> list(
            @RequestParam(defaultValue = "ALL") ResourceStatusFilter status) {
        return resourceService.findResources(status).stream()
                .map(AdminResourceResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
        BookableResource created = resourceService.createResource(
                request.name(),
                request.description(),
                request.type(),
                request.unitPrice(),
                request.totalCapacity());
        return AdminResourceResponse.from(created);
    }

    @PutMapping("/{id}")
    public AdminResourceResponse update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateResourceRequest request) {
        BookableResource updated = resourceService.updateResource(
                id,
                request.version(),
                request.name(),
                request.description(),
                request.type(),
                request.unitPrice(),
                request.totalCapacity());
        return AdminResourceResponse.from(updated);
    }

    @PostMapping("/{id}/activate")
    public AdminResourceResponse activate(@PathVariable Long id) {
        return AdminResourceResponse.from(resourceService.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    public AdminResourceResponse deactivate(@PathVariable Long id) {
        return AdminResourceResponse.from(resourceService.deactivate(id));
    }
}