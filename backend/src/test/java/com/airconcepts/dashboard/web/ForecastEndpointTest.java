package com.airconcepts.dashboard.web;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.forecast.*;
import com.airconcepts.dashboard.forecast.sync.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.entities.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ForecastEndpointTest {
  private final UUID entity=UUID.randomUUID();private final String subject=UUID.randomUUID().toString();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean ForecastAssumptionRepository assumptions;
  @MockitoBean ForecastDocumentRepository documents;
  @MockitoBean com.airconcepts.dashboard.xero.XeroConnectionService connections;
  @MockitoBean com.airconcepts.dashboard.xero.XeroReadHealth health;
  @MockitoBean ForecastAdjustmentRepository adjustments;
  @MockitoBean ForecastNoteRepository notes;
  @MockitoBean ForecastDatasetLoader datasets;
  @MockitoBean org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean org.springframework.transaction.PlatformTransactionManager transactions;
  private String base(){return "/api/v1/entities/"+entity+"/forecast";}
  private void as(Role role){when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));}
  @Test void frUc04_07_roleMatrixOnAssumptionAndSyncRoutes() throws Exception {
    for(Role role:Arrays.asList(Role.STANDARD_USER,null)) {
      as(role);
      for(String path:List.of("/sync","/manual-items","/staffing","/periods/2026-11/opening-balance","/audit"))
        mvc.perform(get(base()+path).header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isForbidden());
      mvc.perform(put(base()+"/periods/2026-11/opening-balance").header("Authorization","Bearer "+TestJwts.TOKEN)
          .contentType("application/json").content("{\"openingBalance\":\"1000.00\"}")).andExpect(status().isForbidden());
    }
    verifyNoInteractions(assumptions,documents,entities);
  }
  @Test void frUc04_04_openingBalanceUsesDecimalStringsAndNoStore() throws Exception {
    when(assumptions.opening(entity,LocalDate.of(2026,11,1))).thenReturn(new ForecastAssumptionRepository.OpeningBalance(new BigDecimal("1000.00"),"Statement"));
    for(var role:List.of(Role.DIRECTOR,Role.FINANCIAL_ADMIN)) {
      as(role);
      mvc.perform(get(base()+"/periods/2026-11/opening-balance").header("Authorization","Bearer "+TestJwts.TOKEN))
          .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
          .andExpect(jsonPath("$.openingBalance").value("1000.00"));
    }
  }
  @Test void frUc04_07_roleMatrixOnEveryRoute() throws Exception {
    var id=UUID.randomUUID();
    var routes=List.of(
        get(base()),get(base()+"/grid?startMonth=2026-11"),post(base()+"/grid/generate"),get(base()+"/months/2026-11/items"),post(base()+"/months/2026-11/generate"),get(base()+"/documents"),get(base()+"/documents/"+id),
        get("/api/v1/forecast/combined"),get(base()+"/sync"),post(base()+"/sync"),
        get(base()+"/periods/2026-11/opening-balance"),put(base()+"/periods/2026-11/opening-balance"),
        get(base()+"/manual-items"),post(base()+"/manual-items"),post(base()+"/manual-items/grid"),patch(base()+"/manual-items/"+id),post(base()+"/manual-items/"+id+"/deactivate"),
        get(base()+"/staffing"),post(base()+"/staffing"),patch(base()+"/staffing/"+id),post(base()+"/staffing/"+id+"/deactivate"),
        get(base()+"/audit"),put(base()+"/documents/"+id+"/adjustment"),put(base()+"/documents/"+id+"/splits"),post(base()+"/documents/"+id+"/conflict"),
        put(base()+"/transactions/DOCUMENT/"+id+"/note"),
        post(base()+"/export"));
    for(Role role:Arrays.asList(Role.STANDARD_USER,null)) {
      as(role);
      for(var request:routes) mvc.perform(request.header("Authorization","Bearer "+TestJwts.TOKEN))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(assumptions,documents,entities,connections,health,notes);
  }
  @Test void notes_directorSavesTrimmedNoteAndBlankClearsIt()throws Exception{
    as(Role.DIRECTOR);var id=UUID.randomUUID();
    when(entities.get(any(),eq(entity))).thenReturn(new Entity(entity,"Synthetic"));
    when(notes.targetExists(entity,"DOCUMENT",id)).thenReturn(true);
    when(notes.upsert(eq(entity),eq("DOCUMENT"),eq(id),eq("Chase on Friday"),any()))
        .thenReturn(new ForecastNoteRepository.TransactionNote("DOCUMENT",id,"Chase on Friday",Instant.parse("2026-11-11T00:00:00Z")));
    mvc.perform(put(base()+"/transactions/DOCUMENT/"+id+"/note").header("Authorization","Bearer "+TestJwts.TOKEN).contentType("application/json")
        .content("{\"note\":\"  Chase on Friday  \"}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.note").value("Chase on Friday")).andExpect(jsonPath("$.sourceId").value(id.toString()));
    mvc.perform(put(base()+"/transactions/DOCUMENT/"+id+"/note").header("Authorization","Bearer "+TestJwts.TOKEN).contentType("application/json")
        .content("{\"note\":\"   \"}")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"note\":null")));
    verify(notes).delete(entity,"DOCUMENT",id);
  }
  @Test void sec_notes_itemOutsideEntityIs404AndNothingIsWritten()throws Exception{
    as(Role.DIRECTOR);var id=UUID.randomUUID();
    when(entities.get(any(),eq(entity))).thenReturn(new Entity(entity,"Synthetic"));
    when(notes.targetExists(entity,"STAFFING",id)).thenReturn(false);
    mvc.perform(put(base()+"/transactions/STAFFING/"+id+"/note").header("Authorization","Bearer "+TestJwts.TOKEN).contentType("application/json")
        .content("{\"note\":\"Not ours\"}")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FORECAST_RECORD_UNAVAILABLE"));
    verify(notes,never()).upsert(any(),any(),any(),any(),any());
    verify(notes,never()).delete(any(),any(),any());
  }
  @Test void frUc04_17_manualRateLimitIs429WithRetryAfter()throws Exception{
    as(Role.FINANCIAL_ADMIN);var run=UUID.randomUUID();var access=new com.airconcepts.dashboard.xero.TenantAccess(entity,UUID.randomUUID(),UUID.randomUUID(),"synthetic");
    when(documents.begin(eq(entity),eq("MANUAL"),any(),any())).thenReturn(run);when(connections.authorisedAccess(any(),eq(entity))).thenReturn(access);
    doThrow(new ProviderFailure("XERO_RATE_LIMITED","Wait",429,45)).when(health).check(access);
    mvc.perform(post(base()+"/sync").header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After","45")).andExpect(jsonPath("$.code").value("XERO_RATE_LIMITED"));
  }
  @Test void sec_frUc04_13_foreignEntityIdIs404()throws Exception {
    as(Role.FINANCIAL_ADMIN);var id=UUID.randomUUID();
    when(assumptions.manualForUpdate(entity,id)).thenReturn(Optional.empty());
    when(assumptions.staffForUpdate(entity,id)).thenReturn(Optional.empty());
    when(adjustments.document(entity,id)).thenThrow(new ApiFailure(org.springframework.http.HttpStatus.NOT_FOUND,"FORECAST_RECORD_NOT_FOUND","Record unavailable."));
    for(var request:List.of(get(base()+"/documents/"+id),patch(base()+"/manual-items/"+id).contentType("application/json").content("{}"),
        patch(base()+"/staffing/"+id).contentType("application/json").content("{}"),put(base()+"/documents/"+id+"/splits").contentType("application/json").content("{\"lines\":[]}")))
      mvc.perform(request.header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isNotFound());
    verify(assumptions,never()).saveManual(any(),any(),any());verify(assumptions,never()).saveStaff(any(),any(),any());
    verify(adjustments,never()).splits(any(),any(),any(),any());
  }
  @Test void frUc04_15_splitSumMismatchIs422AndExclusionReasonRequired()throws Exception {
    as(Role.DIRECTOR);var id=UUID.randomUUID();
    when(adjustments.document(entity,id)).thenReturn(new ForecastAdjustmentRepository.DocumentState(id,new BigDecimal("100.00"),true));
    mvc.perform(put(base()+"/documents/"+id+"/splits").header("Authorization","Bearer "+TestJwts.TOKEN).contentType("application/json")
        .content("{\"lines\":[{\"sequence\":1,\"expectedDate\":\"2026-11-20\",\"amount\":\"99.00\"}]}"))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FORECAST_SPLIT_SUM_MISMATCH"));
    for(String reason:List.of("","x".repeat(501))) mvc.perform(put(base()+"/documents/"+id+"/adjustment").header("Authorization","Bearer "+TestJwts.TOKEN)
        .contentType("application/json").content("{\"excluded\":true,\"exclusionReason\":\""+reason+"\"}"))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FORECAST_EXCLUSION_REASON_REQUIRED"));
    verify(adjustments,never()).splits(any(),any(),any(),any());verify(adjustments,never()).adjust(any(),any(),any(),any());
  }
  @Test void sec_frUc04_15_adjustmentOnSettledDocumentIs409()throws Exception {
    as(Role.DIRECTOR);var id=UUID.randomUUID();
    when(adjustments.document(entity,id)).thenReturn(new ForecastAdjustmentRepository.DocumentState(id,BigDecimal.ZERO,false));
    for(var request:List.of(put(base()+"/documents/"+id+"/splits").content("{\"lines\":[]}"),put(base()+"/documents/"+id+"/adjustment").content("{\"expectedDateOverride\":\"2026-11-20\"}")))
      mvc.perform(request.header("Authorization","Bearer "+TestJwts.TOKEN).contentType("application/json"))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FORECAST_DOCUMENT_NOT_OUTSTANDING"));
    verify(adjustments,never()).splits(any(),any(),any(),any());verify(adjustments,never()).adjust(any(),any(),any(),any());
  }
  private com.airconcepts.dashboard.forecast.calc.ForecastDataset dataset(){
    var month=YearMonth.of(2026,11);var id=UUID.randomUUID();
    return new com.airconcepts.dashboard.forecast.calc.ForecastDataset(entity,"Synthetic",month,LocalDate.of(2026,11,10),new BigDecimal("1000.00"),"Statement",
        List.of(new com.airconcepts.dashboard.forecast.calc.ForecastDataset.Document(id,"ACCREC","INV-PRIVATE","Customer",LocalDate.of(2026,11,1),LocalDate.of(2026,12,15),null,null,new BigDecimal("115.00"),false,null,List.of(),false)),List.of(),
        List.of(new com.airconcepts.dashboard.forecast.calc.ForecastDataset.StaffingLine(UUID.randomUUID(),"PRIVATE_STAFF",new BigDecimal("500.00"),20,month,null,true)),
        new com.airconcepts.dashboard.forecast.calc.ForecastDataset.SyncInfo(Instant.parse("2026-11-01T00:00:00Z"),"FAILED",true,Instant.parse("2026-11-10T00:00:00Z"),"XERO_PROVIDER_FAILED"));
  }
  @Test void frUc04_03_06_staffingNeverInStandardUserOrCombinedDtoAndViewShowsStaleSync()throws Exception {
    var authorised=new Entity(entity,"Synthetic");when(entities.get(any(),eq(entity))).thenReturn(authorised);
    when(datasets.load(eq(authorised),any())).thenReturn(dataset());
    for(var role:List.of(Role.DIRECTOR,Role.FINANCIAL_ADMIN)){
      as(role);
      mvc.perform(get(base()).param("financialYearStart","2026").header("Authorization","Bearer "+TestJwts.TOKEN))
          .andExpect(status().isOk()).andExpect(jsonPath("$.syncStale").value(true)).andExpect(jsonPath("$.lastRun.status").value("FAILED"))
          .andExpect(jsonPath("$.lastSuccessfulSyncAt").value("2026-11-01T00:00:00Z"));
      mvc.perform(get("/api/v1/forecast/combined").param("entityIds",entity.toString()).param("financialYearStart","2026").header("Authorization","Bearer "+TestJwts.TOKEN))
          .andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(false)).andExpect(jsonPath("$..staffing").doesNotExist())
          .andExpect(jsonPath("$..documents").doesNotExist()).andExpect(jsonPath("$..items").doesNotExist())
          .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE_STAFF"))));
    }
    as(Role.STANDARD_USER);
    mvc.perform(get(base()+"/staffing").header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isForbidden())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE_STAFF"))));
  }
  @Test void frUc04_20_monthItemsGroupByForecastDateNotIssueMonth()throws Exception {
    as(Role.FINANCIAL_ADMIN);var authorised=new Entity(entity,"Synthetic");when(entities.get(any(),eq(entity))).thenReturn(authorised);
    when(datasets.load(eq(authorised),any())).thenReturn(dataset());
    // Issued in November but due in December: the November preview does not carry it, so it is grouped by forecast date.
    mvc.perform(get(base()+"/months/2026-11/items").param("direction","IN").header("Authorization","Bearer "+TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PREVIEW")).andExpect(jsonPath("$.generatable").value(true))
        .andExpect(jsonPath("$.items").isEmpty());
    // December cannot be previewed until November is generated, so its items are not shown.
    mvc.perform(get(base()+"/months/2026-12/items").param("direction","IN").header("Authorization","Bearer "+TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NOT_GENERATED")).andExpect(jsonPath("$.items").isEmpty());
  }
  @Test void frUc04_13_archivedEntityFailsWholeRequest()throws Exception {
    as(Role.DIRECTOR);var unknown=UUID.randomUUID();var authorised=new Entity(entity,"Synthetic");
    when(entities.get(any(),eq(entity))).thenReturn(authorised);when(datasets.load(eq(authorised),any())).thenReturn(dataset());
    when(entities.get(any(),eq(unknown))).thenThrow(new ApiFailure(org.springframework.http.HttpStatus.NOT_FOUND,"ENTITY_UNAVAILABLE","Entity unavailable."));
    mvc.perform(get("/api/v1/forecast/combined").param("entityIds",entity+","+unknown).param("financialYearStart","2026").header("Authorization","Bearer "+TestJwts.TOKEN))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.entities").doesNotExist());
  }
  @Test void frUc04_16_conflictResolution()throws Exception {
    as(Role.FINANCIAL_ADMIN);var id=UUID.randomUUID();
    when(adjustments.document(entity,id)).thenReturn(new ForecastAdjustmentRepository.DocumentState(id,new BigDecimal("115.00"),true));
    for(String resolution:List.of("KEEP_SCHEDULE","DISCARD_SCHEDULE")){
      mvc.perform(post(base()+"/documents/"+id+"/conflict").header("Authorization","Bearer "+TestJwts.TOKEN).contentType("application/json").content("{\"resolution\":\""+resolution+"\"}"))
          .andExpect(status().isOk());
      verify(adjustments).resolve(entity,id,UUID.fromString(subject),resolution);
    }
  }
}
