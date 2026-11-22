package com.airconcepts.dashboard.vwork;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class VworkTemplatesServiceTest {
  private final VworkTemplatesRepository templates = mock(VworkTemplatesRepository.class);
  private final GreenLineSettingsRepository settings = mock(GreenLineSettingsRepository.class);
  private final VworkTemplatesService service = new VworkTemplatesService(templates, settings);
  private final UUID entity = UUID.randomUUID(), actor = UUID.randomUUID();
  private final CurrentUser director = new CurrentUser(actor.toString(), Role.DIRECTOR);
  private final VworkTemplatesRepository.Template one = new VworkTemplatesRepository.Template("template-1", true);

  @BeforeEach void setup() {
    when(settings.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(settings.lockEntity(entity)).thenReturn(true);
    when(templates.read(entity)).thenReturn(List.of(one));
  }

  @Test void gl06_directorSavesValidatedEntitySetUnderLocks() {
    assertThat(service.save(director, entity, List.of(one))).containsExactly(one);
    var order = inOrder(settings, templates);
    order.verify(settings).lockActor(actor);
    order.verify(templates).lockAssignments();
    order.verify(settings).lockEntity(entity);
    order.verify(templates).replace(entity, actor, List.of(one));
  }

  @Test void gl06_rejectsInactiveDirectorAndMissingEntity() {
    when(settings.lockActor(actor)).thenReturn(Optional.of(Role.STANDARD_USER));
    assertThatThrownBy(() -> service.save(director, entity, List.of(one))).isInstanceOf(AccessDeniedException.class);
    verify(templates, never()).replace(any(), any(), any());
    when(settings.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(settings.lockEntity(entity)).thenReturn(false);
    assertThatThrownBy(() -> service.save(director, entity, List.of(one))).isInstanceOf(ApiFailure.class);
    verify(templates, never()).replace(any(), any(), any());
  }

  @Test void gl06_rejectsEmptyDuplicateMultipleDefaultsAndUnsafeReferences() {
    for (var input : List.of(List.<VworkTemplatesRepository.Template>of(), List.of(one, one),
        List.of(one, new VworkTemplatesRepository.Template("other", true)),
        List.of(new VworkTemplatesRepository.Template("  ", true)),
        List.of(new VworkTemplatesRepository.Template("x".repeat(121), true)),
        List.of(new VworkTemplatesRepository.Template("x", false)))) {
      assertThatThrownBy(() -> service.save(director, entity, input)).isInstanceOf(ApiFailure.class);
    }
    verifyNoInteractions(templates, settings);
  }

  @Test void gl06_otherRolesReadButCannotChange() {
    when(settings.entityExists(entity)).thenReturn(true);
    var user = new CurrentUser(actor.toString(), Role.STANDARD_USER);
    assertThat(service.get(user, entity)).containsExactly(one);
    assertThatThrownBy(() -> service.save(user, entity, List.of(one))).isInstanceOf(AccessDeniedException.class);
    verify(templates, never()).replace(any(), any(), any());
  }
}
