package com.airconcepts.dashboard.forecast.calc;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Canonical immutable calculation input. No clock, database, Spring or provider access. */
public record ForecastDataset(UUID entityId,String entityName,YearMonth startMonth,LocalDate today,
    BigDecimal openingBalance,String openingBalanceSource,List<Document> documents,List<ManualItem> manualItems,
    List<StaffingLine> staffing,SyncInfo sync) {
  public ForecastDataset {documents=List.copyOf(documents);manualItems=List.copyOf(manualItems);staffing=List.copyOf(staffing);}
  public record Split(int sequence,LocalDate expectedDate,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal amount) {}
  public record Document(UUID id,String type,String number,String contact,LocalDate issueDate,LocalDate dueDate,
      LocalDate xeroExpectedDate,LocalDate overrideDate,BigDecimal amountDue,boolean excluded,String exclusionReason,
      List<Split> splits,boolean dateConflict) {
    public Document {splits=List.copyOf(splits);}
  }
  public record ManualItem(UUID id,String direction,String category,String description,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal amount,String recurrence,
      Integer dayOfMonth,YearMonth startMonth,YearMonth endMonth,boolean active) {}
  public record StaffingLine(UUID id,String label,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal monthlyCost,Integer payDayOfMonth,YearMonth startMonth,YearMonth endMonth,boolean active,
      @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal",nullable=true) BigDecimal hourlyRate,
      @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal",nullable=true) BigDecimal weeklyHours,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String paymentFrequency,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="date") LocalDate firstPayday,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="date") LocalDate employmentStart,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="date") LocalDate employmentEnd) {
    public StaffingLine(UUID id,String label,BigDecimal monthlyCost,Integer payDayOfMonth,YearMonth startMonth,YearMonth endMonth,boolean active) {
      this(id,label,monthlyCost,payDayOfMonth,startMonth,endMonth,active,null,null,null,null,null,null);
    }
  }
  public record SyncInfo(Instant lastSuccessfulSyncAt,String lastRunStatus,boolean stale,Instant lastRunFinishedAt,String lastErrorCode) {
    public SyncInfo(Instant lastSuccessfulSyncAt,String lastRunStatus,boolean stale){this(lastSuccessfulSyncAt,lastRunStatus,stale,null,null);}
  }
}
