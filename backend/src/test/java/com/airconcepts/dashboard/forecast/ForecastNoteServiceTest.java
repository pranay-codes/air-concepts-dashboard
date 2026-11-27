package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.forecast.ForecastNoteRepository.TransactionNote;
import com.airconcepts.dashboard.forecast.calc.DateRule;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class ForecastNoteServiceTest {
  private final ForecastNoteRepository repository = mock(ForecastNoteRepository.class);
  private final EntityService entities = mock(EntityService.class);
  private final ForecastNoteService service = new ForecastNoteService(repository, new ForecastAccess(entities));
  private final UUID entity = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();
  private final CurrentUser director = new CurrentUser(actor.toString(), Role.DIRECTOR);
  private final Instant savedAt = Instant.parse("2026-11-11T00:00:00Z");

  ForecastNoteServiceTest() {
    when(entities.get(director, entity)).thenReturn(new Entity(entity, "Synthetic"));
    when(repository.targetExists(entity, "DOCUMENT", item)).thenReturn(true);
    when(repository.upsert(eq(entity), eq("DOCUMENT"), eq(item), anyString(), eq(actor)))
        .thenAnswer(i -> new TransactionNote("DOCUMENT", item, i.getArgument(3), savedAt));
  }

  @Test void notes_saveTrimsTheTextAndKeysItToTheAuthorisedEntity() {
    var saved = service.save(director, entity, "DOCUMENT", item, "  Call Tuesday about the deposit  ");
    verify(repository).upsert(entity, "DOCUMENT", item, "Call Tuesday about the deposit", actor);
    assertThat(saved.note()).isEqualTo("Call Tuesday about the deposit");
    assertThat(saved.updatedAt()).isEqualTo(savedAt);
  }

  @Test void notes_blankTextRemovesTheNoteAndWritesNothingElse() {
    var saved = service.save(director, entity, "DOCUMENT", item, "   \n\t ");
    verify(repository).delete(entity, "DOCUMENT", item);
    verify(repository, never()).upsert(any(), any(), any(), any(), any());
    assertThat(saved.note()).isNull();
  }

  @Test void notes_missingTextIsRejected() {
    assertInvalid(() -> service.save(director, entity, "DOCUMENT", item, null));
    verifyNoInteractions(repository);
  }

  @Test void notes_textOverTwoThousandCharactersIsRejected() {
    assertInvalid(() -> service.save(director, entity, "DOCUMENT", item, "a".repeat(2001)));
    verify(repository, never()).upsert(any(), any(), any(), any(), any());
  }

  @Test void notes_lengthIsCountedInCodePointsNotUtf16Units() {
    assertThatCode(() -> service.save(director, entity, "DOCUMENT", item, "😀".repeat(2000))).doesNotThrowAnyException();
    assertInvalid(() -> service.save(director, entity, "DOCUMENT", item, "😀".repeat(2001)));
  }

  @Test void notes_controlCharactersAreRejectedButLineBreaksAreKept() {
    assertInvalid(() -> service.save(director, entity, "DOCUMENT", item, "bell\u0007character"));
    assertThat(ForecastNoteService.normalise("line one\nline two\tend")).isEqualTo("line one\nline two\tend");
  }

  @Test void notes_unknownSourceIsRejectedBeforeAnyLookup() {
    assertInvalid(() -> service.save(director, entity, "XERO", item, "anything"));
    verifyNoInteractions(repository);
  }

  @Test void notes_itemFromAnotherEntityIsNotFoundAndNothingIsWritten() {
    var other = UUID.randomUUID();
    when(repository.targetExists(entity, "MANUAL", other)).thenReturn(false);
    assertThatThrownBy(() -> service.save(director, entity, "MANUAL", other, "Note"))
        .isInstanceOfSatisfying(ApiFailure.class, e -> {
          assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(e.code()).isEqualTo("FORECAST_RECORD_UNAVAILABLE");
        });
    verify(repository, never()).upsert(any(), any(), any(), any(), any());
    verify(repository, never()).delete(any(), any(), any());
  }

  @Test void notes_missingItemIdIsNotFound() {
    assertThatThrownBy(() -> service.save(director, entity, "DOCUMENT", null, "Note"))
        .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("FORECAST_RECORD_UNAVAILABLE"));
    verifyNoInteractions(repository);
  }

  @Test void notes_standardUserCannotWrite() {
    var standard = new CurrentUser(actor.toString(), Role.STANDARD_USER);
    when(entities.get(standard, entity)).thenReturn(new Entity(entity, "Synthetic"));
    assertThatThrownBy(() -> service.save(standard, entity, "DOCUMENT", item, "Note"))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository);
  }

  @Test void notes_userWithoutEntityAccessCannotWrite() {
    var stranger = new CurrentUser(actor.toString(), Role.DIRECTOR);
    var otherEntity = UUID.randomUUID();
    when(entities.get(stranger, otherEntity)).thenThrow(new ApiFailure(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", "Not found"));
    assertThatThrownBy(() -> service.save(stranger, otherEntity, "DOCUMENT", item, "Note"))
        .isInstanceOf(ApiFailure.class);
    verifyNoInteractions(repository);
  }

  @Test void frUc04_companyNotesAreScopedAndIndependent() {
    when(repository.companyExists(entity, "Bayside Club")).thenReturn(true);
    service.saveCompany(director, entity, "Bayside Club", " Call them ");
    verify(repository).upsertCompany(entity, "Bayside Club", "Call them", actor);
    service.saveCompany(director, entity, "Bayside Club", " ");
    verify(repository).deleteCompany(entity, "Bayside Club");
    verify(repository, never()).upsert(any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.saveCompany(director, entity, "Other", "x")).isInstanceOf(ApiFailure.class);
    var standard = new CurrentUser(actor.toString(), Role.STANDARD_USER);
    when(entities.get(standard, entity)).thenReturn(new Entity(entity, "Synthetic"));
    assertThatThrownBy(() -> service.saveCompany(standard, entity, "Bayside Club", "x")).isInstanceOf(AccessDeniedException.class);
  }

  private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call).isInstanceOfSatisfying(ApiFailure.class, e -> {
      assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
      assertThat(e.code()).isEqualTo("FORECAST_INPUT_INVALID");
    });
  }

  @Test void notes_itemsOnlyCarryNotesForTransactionsInThatMonth() {
    var shown = UUID.randomUUID();
    var hidden = UUID.randomUUID();
    var items = List.of(new Contribution(shown, "DOCUMENT", "IN", "ACCREC", "INV-0017", "City Limousines",
        LocalDate.of(2026, 11, 20), YearMonth.of(2026, 11), new BigDecimal("250.00"), DateRule.DUE_DATE, false, null, null));
    var notes = List.of(
        new TransactionNote("DOCUMENT", shown, "Shown", savedAt),
        new TransactionNote("DOCUMENT", hidden, "Other month", savedAt),
        new TransactionNote("MANUAL", shown, "Same id, different source", savedAt));
    assertThat(ForecastService.notesFor(items, notes)).containsExactly(notes.getFirst());
  }
}
