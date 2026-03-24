package com.expensetracker.controller;

import com.expensetracker.config.UserPrincipal;
import com.expensetracker.dto.request.CategoryRequest;
import com.expensetracker.dto.response.CategoryResponse;
import com.expensetracker.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CategoryResponse> categories = categoryService.listCategories(principal.getUserId());
        return ResponseEntity.ok(categories);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> renameCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response =
                categoryService.renameCategory(principal.getUserId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        categoryService.deleteCategory(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
