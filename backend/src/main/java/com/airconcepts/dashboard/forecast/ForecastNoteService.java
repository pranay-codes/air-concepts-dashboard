package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.forecast.ForecastNoteRepository.TransactionNote;
import com.airconcepts.dashboard.security.CurrentUser;
import java.util.*;
import org.springframework.stereotype.Service;

/** Writes internal notes for one forecast transaction. Callers must be authorised for the entity. */
@Service
public class ForecastNoteService {
  public static final int MAX_NOTE_CODE_POINTS = 2000;
  private static final Set<String> SOURCES = Set.of("DOCUMENT", "MANUAL", "STAFFING");

  public record NoteRequest(String note) {}

  private final ForecastNoteRepository repository;
  private final ForecastAccess access;

  public ForecastNoteService(ForecastNoteRepository repository, ForecastAccess access) {
    this.repository = repository;
    this.access = access;
  }

  /**
   * Saves the note for one transaction. A blank note removes it. Saving the same transaction again
   * updates the existing row, so repeated requests never create duplicate notes.
   */
  public TransactionNote save(CurrentUser user, UUID entity, String source, UUID sourceId, String text) {
    var authorised = access.requireWrite(user, entity);
    if (source == null || !SOURCES.contains(source))
      throw ForecastValidation.invalid("source", "Choose a document, manual item or staffing line.");
    if (sourceId == null) throw ForecastValidation.missing();
    var normalised = normalise(text);
    if (!repository.targetExists(authorised.id(), source, sourceId)) throw ForecastValidation.missing();
    if (normalised == null) {
      repository.delete(authorised.id(), source, sourceId);
      return new TransactionNote(source, sourceId, null, null);
    }
    return repository.upsert(authorised.id(), source, sourceId, normalised, UUID.fromString(user.subject()));
  }

  public ForecastNoteRepository.CompanyNote saveCompany(CurrentUser user, UUID entity, String company, String text) {
    var authorised = access.requireWrite(user, entity);
    if (company == null || company.isBlank() || company.length() > 500 || "Unknown contact".equals(company))
      throw ForecastValidation.invalid("company", "Choose a company.");
    var normalised = normalise(text);
    if (!repository.companyExists(authorised.id(), company)) throw ForecastValidation.missing();
    if (normalised == null) {
      repository.deleteCompany(authorised.id(), company);
      return new ForecastNoteRepository.CompanyNote(company, null);
    }
    return repository.upsertCompany(authorised.id(), company, normalised, UUID.fromString(user.subject()));
  }

  /** Strips surrounding whitespace, returns null for blank input, and rejects control characters other than line breaks and tabs. */
  static String normalise(String text) {
    if (text == null) throw ForecastValidation.invalid("note", "Enter a note of up to 2000 characters.");
    var stripped = text.strip();
    if (stripped.isEmpty()) return null;
    if (stripped.codePointCount(0, stripped.length()) > MAX_NOTE_CODE_POINTS)
      throw ForecastValidation.invalid("note", "Enter a note of up to 2000 characters.");
    if (stripped.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
      throw ForecastValidation.invalid("note", "Remove control characters from the note.");
    return stripped;
  }
}
