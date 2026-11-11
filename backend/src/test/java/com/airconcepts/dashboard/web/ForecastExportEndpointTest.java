package com.airconcepts.dashboard.web;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.*;
import com.airconcepts.dashboard.forecast.*;
import com.airconcepts.dashboard.security.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ForecastExportEndpointTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;@MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;@MockitoBean ForecastDatasetLoader loader;@MockitoBean ForecastExportRepository exports;
  @MockitoBean ForecastMonthRepository months;@MockitoBean ForecastAssumptionRepository assumptions;
  @org.springframework.beans.factory.annotation.Autowired ForecastCalendar calendar;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.airconcepts.dashboard.forecast.excel.WorkbookPopulator populator;
  private final UUID entity=UUID.randomUUID();private final String subject=UUID.randomUUID().toString();
  private void as(Role role){when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));}
  private String path(){return "/api/v1/entities/"+entity+"/forecast/export";}
  /** A financial year in which every month is generated and carries an opening balance, so the export has no coverage warnings. */
  private void generatedFinancialYear(){
    var start=calendar.currentFinancialYearStart();Map<java.time.YearMonth,ForecastMonthRepository.GeneratedMonth> generated=new LinkedHashMap<>();
    for(int i=0;i<12;i++) generated.put(start.plusMonths(i),new ForecastMonthRepository.GeneratedMonth(start.plusMonths(i),java.time.Instant.now(),1));
    when(months.generated(entity)).thenReturn(generated);when(months.placement(entity)).thenReturn(com.airconcepts.dashboard.forecast.calc.ForecastPlacement.Placed.none());
    when(assumptions.openingAnchors(entity)).thenReturn(Map.of(start,new ForecastAssumptionRepository.OpeningBalance(new java.math.BigDecimal("1000.00"),"Statement")));
  }
  @Test void frUc04_12_invalidUploadLeavesTablesUnchanged()throws Exception{
    as(Role.FINANCIAL_ADMIN);when(entities.get(any(),eq(entity))).thenReturn(new Entity(entity,"Synthetic"));
    mvc.perform(multipart(path()).file(new MockMultipartFile("file","invalid.xlsx","application/octet-stream",new byte[]{1,2,3}))
        .header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.problems").isArray());
    verifyNoInteractions(exports,loader);
  }
  @Test void frUc04_07_standardUserCannotExportOrExposeStaffing()throws Exception{
    as(Role.STANDARD_USER);
    mvc.perform(multipart(path()).file(new MockMultipartFile("file","template.xlsx","application/octet-stream",new byte[]{1}))
        .header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isForbidden());
    verifyNoInteractions(exports,loader,entities);
  }
  @Test void frUc04_19_headersFilenameAndSingleAuditWrite()throws Exception{
    as(Role.FINANCIAL_ADMIN);var authorised=new Entity(entity,"Synthetic");when(entities.get(any(),eq(entity))).thenReturn(authorised);when(exports.entityCode(entity)).thenReturn("synthetic"); generatedFinancialYear();
    when(loader.load(eq(authorised),any())).thenAnswer(invocation->{var month=invocation.getArgument(1,java.time.YearMonth.class);return new com.airconcepts.dashboard.forecast.calc.ForecastDataset(entity,"Synthetic",month,java.time.LocalDate.now(),new java.math.BigDecimal("1000.00"),"Statement",List.of(),List.of(),List.of(),null);});
    mvc.perform(multipart(path()).file(new MockMultipartFile("file","template.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",Files.readAllBytes(Path.of("src/test/resources/forecast/template-v1.xlsx"))))
        .header("Authorization","Bearer "+TestJwts.TOKEN).header("Origin","http://localhost:5173"))
        .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
        .andExpect(header().string("X-Forecast-Reconciled","true"))
        .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.matchesPattern("attachment; filename=\"forecast-synthetic-\\d{4}-\\d{2}-\\d{8}-\\d{4}\\.xlsx\"")))
        .andExpect(header().string("Access-Control-Expose-Headers",org.hamcrest.Matchers.containsString("X-Forecast-Reconciled")));
    verify(exports,times(1)).record(eq(entity),eq(UUID.fromString(subject)),any(),any(),eq(true),eq(List.of()),any());
  }
  @Test void frUc04_21_injectedMismatchSetsHeaderAndFlagButStillReturnsWorkbook()throws Exception{
    as(Role.FINANCIAL_ADMIN);var authorised=new Entity(entity,"Synthetic");when(entities.get(any(),eq(entity))).thenReturn(authorised);when(exports.entityCode(entity)).thenReturn("synthetic"); generatedFinancialYear();
    when(loader.load(eq(authorised),any())).thenAnswer(invocation->new com.airconcepts.dashboard.forecast.calc.ForecastDataset(entity,"Synthetic",invocation.getArgument(1),java.time.LocalDate.now(),new java.math.BigDecimal("1000.00"),"Statement",List.of(),List.of(),List.of(),null));
    doAnswer(invocation->{
      var warnings=invocation.callRealMethod();
      var workbook=invocation.getArgument(0,org.apache.poi.xssf.usermodel.XSSFWorkbook.class);
      workbook.getSheet("Assumptions").getRow(4).getCell(1).setCellValue(1001.00);
      return warnings;
    }).when(populator).populate(any(),any(),any(),any());
    mvc.perform(multipart(path()).file(new MockMultipartFile("file","template.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",Files.readAllBytes(Path.of("src/test/resources/forecast/template-v1.xlsx"))))
        .header("Authorization","Bearer "+TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("X-Forecast-Reconciled","false"))
        .andExpect(header().string("X-Forecast-Warnings",org.hamcrest.Matchers.containsString("1001")))
        .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    verify(exports,times(1)).record(eq(entity),eq(UUID.fromString(subject)),any(),any(),eq(false),argThat(warnings->warnings.stream().anyMatch(w->w.contains("1001")&&w.contains("1000"))),any());
  }
}
