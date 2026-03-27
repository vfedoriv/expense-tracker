package com.expensetracker.category;

import com.expensetracker.category.dto.CategoryRequest;
import com.expensetracker.category.dto.CategoryResponse;
import com.expensetracker.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        return categoryService.findAllByUser(principal.getId())
            .stream()
            .map(CategoryResponse::from)
            .toList();
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CategoryRequest request
    ) {
        Category category = categoryService.create(principal.getId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(category));
    }

    @PutMapping("/{id}")
    public CategoryResponse rename(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CategoryRequest request
    ) {
        Category category = categoryService.rename(id, principal.getId(), request.name());
        return CategoryResponse.from(category);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        categoryService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
