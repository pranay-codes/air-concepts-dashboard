package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.VworkTemplatesRepository.Template;
import com.airconcepts.dashboard.vwork.VworkTemplatesService;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/entities/{entityId}/vwork-templates", produces = MediaType.APPLICATION_JSON_VALUE)
public class VworkTemplatesController {
  @Schema(name = "EntityVworkTemplate", requiredProperties = {"templateRef", "isDefault"})
  public record TemplateResponse(String templateRef, @JsonProperty("isDefault") boolean isDefault) {
    static TemplateResponse from(Template item) { return new TemplateResponse(item.templateRef(), item.isDefault()); }
  }
  @Schema(name = "EntityVworkTemplateInput", requiredProperties = {"templateRef", "isDefault"})
  public record TemplateInput(@NotNull @Size(min = 1, max = 120) String templateRef,
      @NotNull @JsonProperty("isDefault") Boolean isDefault) {}
  @Schema(name = "EntityVworkTemplatesRequest", requiredProperties = "templates")
  public record TemplatesRequest(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid TemplateInput> templates) {}

  private final VworkTemplatesService templates;
  private final CurrentUserResolver users;
  public VworkTemplatesController(VworkTemplatesService templates, CurrentUserResolver users) {
    this.templates = templates; this.users = users;
  }

  @GetMapping
  @Operation(operationId = "listEntityVworkTemplates", summary = "Read an entity's vWork template assignments")
  public ResponseEntity<List<TemplateResponse>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    return response(templates.get(user, entityId));
  }

  @PutMapping
  @Operation(operationId = "updateEntityVworkTemplates", summary = "Replace an entity's vWork templates (Director only)")
  public ResponseEntity<List<TemplateResponse>> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @Valid @RequestBody TemplatesRequest request) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR);
    var desired = request.templates().stream().map(item -> new Template(item.templateRef(), item.isDefault())).toList();
    return response(templates.save(user, entityId, desired));
  }

  private static ResponseEntity<List<TemplateResponse>> response(List<Template> items) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items.stream().map(TemplateResponse::from).toList());
  }
}
