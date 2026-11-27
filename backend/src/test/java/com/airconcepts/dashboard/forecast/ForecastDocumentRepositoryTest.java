package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.DockerRequirement;
import com.airconcepts.dashboard.forecast.sync.ForecastDocumentRepository;
import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
/** CI/disposable database only; explicitly excluded from all no-Docker local runs. */
class ForecastDocumentRepositoryTest {
  private static PostgreSQLContainer postgres;private static JdbcTemplate jdbc;private static ForecastDocumentRepository repository;
  private UUID entity,tenant,grant,actor,id,run;private TenantAccess access;private Instant now;
  @BeforeAll static void start()throws Exception{
    DockerRequirement.check("true".equals(System.getenv("CI")),DockerClientFactory.instance().isDockerAvailable());
    postgres=new PostgreSQLContainer("postgres:17-alpine");postgres.start();
    var dataSource=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());jdbc=new JdbcTemplate(dataSource);
    jdbc.execute("create role anon;create role authenticated;create role service_role;create role supabase_auth_admin;create schema auth;create table auth.users(id uuid primary key)");
    try(var files=Files.list(Path.of("..","supabase","migrations"))){for(var file:files.filter(f->f.toString().endsWith(".sql")).sorted().toList())jdbc.execute(Files.readString(file));}
    repository=new ForecastDocumentRepository(jdbc,new DataSourceTransactionManager(dataSource));
  }
  @AfterAll static void stop(){if(postgres!=null)postgres.stop();}
  @BeforeEach void setup(){
    entity=UUID.randomUUID();tenant=UUID.randomUUID();grant=UUID.randomUUID();actor=UUID.randomUUID();id=UUID.randomUUID();now=Instant.now();
    jdbc.update("insert into auth.users(id) values(?)",actor);jdbc.update("insert into public.staff_users(id,email,display_name,role) values(?,?,?,'DIRECTOR')",actor,actor+"@example.invalid","Synthetic");
    jdbc.update("insert into public.entities(id,code,display_name) values(?,?,?)",entity,"forecast-"+entity,"Synthetic");
    jdbc.update("insert into public.xero_grants(id,access_ciphertext,refresh_ciphertext,expires_at,status) values(?,?,?,now()+interval '1 hour','ACTIVE')",grant,"synthetic-ciphertext-placeholder-0001","synthetic-ciphertext-placeholder-0002");
    jdbc.update("insert into public.xero_entity_connections(entity_id,grant_id,tenant_id,connection_id,tenant_name,verified_at) values(?,?,?,?,?,now())",entity,grant,tenant,UUID.randomUUID(),"Synthetic");
    access=new TenantAccess(entity,tenant,grant,"synthetic-access");run=repository.begin(entity,"MANUAL",actor,now);
  }
  private ForecastSource source(UUID document,String amount,LocalDate expected){return new ForecastSource(document,InvoiceType.ACCREC,"INV-1",null,"Synthetic",InvoiceStatus.AUTHORISED,
      LocalDate.of(2026,11,1),LocalDate.of(2026,11,15),expected,"NZD",new BigDecimal("115.00"),new BigDecimal("15.00"),new BigDecimal(amount),BigDecimal.ZERO,BigDecimal.ZERO,now);}
  private void apply(List<ForecastSource> documents,List<UUID> deleted){repository.apply(entity,run,access,documents,deleted,new ForecastSource.Limits(999,49,9999),now);}
  @Test void frUc04_01_upsertTwiceYieldsOneUnchangedRow(){
    var document=source(id,"115.00",null);apply(List.of(document),List.of());
    var before=jdbc.queryForMap("select * from public.forecast_documents where entity_id=? and xero_document_id=?",entity,id);
    apply(List.of(document),List.of());assertThat(jdbc.queryForMap("select * from public.forecast_documents where entity_id=? and xero_document_id=?",entity,id)).isEqualTo(before);
    assertThat(repository.get(entity,run).documentsInserted()).isEqualTo(1);assertThat(repository.get(entity,run).documentsUpdated()).isZero();
  }
  @Test void frUc04_17_settledDocumentFlipsOutstanding(){
    apply(List.of(source(id,"115.00",null)),List.of());apply(List.of(),List.of(id));
    var row=jdbc.queryForMap("select outstanding,status,settled_at from public.forecast_documents where entity_id=? and xero_document_id=?",entity,id);
    assertThat(row.get("outstanding")).isEqualTo(false);assertThat(row.get("status")).isEqualTo("DELETED");assertThat(row.get("settled_at")).isNotNull();
  }
  @Test void frUc04_17_adjustmentsUntouchedBySyncAndDateChangeFlagsConflict(){
    apply(List.of(source(id,"115.00",LocalDate.of(2026,11,15))),List.of());
    jdbc.update("insert into public.forecast_document_adjustments(entity_id,xero_document_id,expected_date_override,excluded,exclusion_reason,updated_by) values(?,?,? ,true,'Disputed',?)",entity,id,LocalDate.of(2026,11,30),actor);
    UUID split=UUID.randomUUID();jdbc.update("insert into public.forecast_split_lines(id,entity_id,xero_document_id,sequence,expected_date,amount) values(?,?,?,1,?,115)",split,entity,id,LocalDate.of(2026,12,1));
    apply(List.of(source(id,"100.00",LocalDate.of(2026,11,20))),List.of());
    var row=jdbc.queryForMap("select * from public.forecast_document_adjustments where entity_id=? and xero_document_id=?",entity,id);
    assertThat(row.get("excluded")).isEqualTo(true);assertThat(row.get("exclusion_reason")).isEqualTo("Disputed");assertThat(row.get("date_conflict_since")).isNotNull();
    assertThat(jdbc.queryForObject("select amount from public.forecast_split_lines where entity_id=? and id=?",BigDecimal.class,entity,split)).isEqualByComparingTo("115.00");
  }
  @Test void frUc04_17_typeTransactionRollsBackAllRowsOnDatabaseFailure(){
    assertThatThrownBy(()->apply(List.of(source(id,"115.00",null),source(UUID.randomUUID(),"-1.00",null)),List.of())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("select count(*) from public.forecast_documents where entity_id=?",Integer.class,entity)).isZero();
  }
  @Test void frUc04_manualSnapshotsKeepPastAmountsOnEditAndDeactivation(){
    var current=YearMonth.now(ForecastMonths.ZONE);var previous=current.minusMonths(1);var manualId=UUID.randomUUID();
    for(var month:List.of(previous,current)) jdbc.update("insert into public.forecast_grid_revisions(entity_id,forecast_month,revision,created_by) values(?,?,1,?)",entity,month.atDay(1),actor);
    var assumptions=new ForecastAssumptionRepository(jdbc,new tools.jackson.databind.ObjectMapper());
    var initial=new com.airconcepts.dashboard.forecast.calc.ForecastDataset.ManualItem(manualId,"IN","OTHER_RECEIPT","Original",new BigDecimal("25.00"),"MONTHLY",1,previous,null,true);
    assumptions.saveManual(entity,actor,initial);
    jdbc.update("insert into public.forecast_grid_manual_snapshots(entity_id,forecast_month,manual_item_id,direction,description,amount,active) values(?,?,?,'IN','Original',25.00,true)",entity,previous.atDay(1),manualId);
    var edited=new com.airconcepts.dashboard.forecast.calc.ForecastDataset.ManualItem(manualId,"IN","OTHER_RECEIPT","Revised",new BigDecimal("50.00"),"MONTHLY",1,previous,null,true);
    assumptions.saveManual(entity,actor,edited);
    var grid=new ForecastGridRepository(jdbc);
    assertThat(grid.manualLines(entity,previous,current)).extracting(ForecastGridRepository.ManualLine::amount).containsExactly(new BigDecimal("25.00"),new BigDecimal("50.00"));
    assertThat(grid.manualLines(UUID.randomUUID(),previous,current)).isEmpty();
    assumptions.saveManual(entity,actor,new com.airconcepts.dashboard.forecast.calc.ForecastDataset.ManualItem(manualId,"IN","OTHER_RECEIPT","Revised",new BigDecimal("50.00"),"MONTHLY",1,previous,null,false));
    assertThat(grid.manualLines(entity,previous,current)).singleElement().satisfies(line->{assertThat(line.month()).isEqualTo(previous);assertThat(line.amount()).isEqualByComparingTo("25.00");});
  }
}
