package com.airconcepts.dashboard.workitems;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record WorkItemListQuery(UUID entityId, WorkItemStage stage, StageMachine.QuoteStatus xeroStatus,
    UUID ownerId, LocalDate followUpDueBy, Boolean late, String search, Sort sort, Direction direction,
    int page, int pageSize) {
  public enum Sort { stageEnteredAt, quoteNumber, customer, followUpDate, total }
  public enum Direction { ASC, DESC }
  public WorkItemListQuery {
    Objects.requireNonNull(sort, "sort");
    Objects.requireNonNull(direction, "direction");
    search = search == null ? "" : search;
    if (search.length() > 200 || search.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid search");
    search = search.strip();
    if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid page");
  }
  public long offset() { return ((long) page - 1) * pageSize; }
  static String likePattern(String text) {
    return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
