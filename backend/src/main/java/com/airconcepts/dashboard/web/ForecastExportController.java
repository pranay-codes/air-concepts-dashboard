package com.airconcepts.dashboard.web;
import com.airconcepts.dashboard.forecast.*;
import com.airconcepts.dashboard.forecast.excel.*;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
@RestController @SecurityRequirement(name="bearerAuth")
@RequestMapping("/api/v1/entities/{entityId}/forecast/export")
public class ForecastExportController {
  public static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private final ForecastExportService service;private final CurrentUserResolver users;private final ForecastAccess access;private final ObjectMapper mapper;
  public ForecastExportController(ForecastExportService service,CurrentUserResolver users,ForecastAccess access,ObjectMapper mapper){this.service=service;this.users=users;this.access=access;this.mapper=mapper;}
  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE,produces=XLSX) @Operation(operationId="exportForecast")
  public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(required=false) String startMonth,@RequestPart("file") MultipartFile file)throws IOException{
    var user=users.from(jwt);access.requireRead(user,entityId);
    if(file.getSize()>WorkbookValidator.MAX_UPLOAD_BYTES)throw tooLarge();
    var result=service.export(user,entityId,startMonth,file.getOriginalFilename(),file.getBytes());
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX)).cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.filename()).build().toString())
        .header("X-Forecast-Reconciled",Boolean.toString(result.reconciled())).header("X-Forecast-Generated-At",result.generatedAt().toString())
        .header("X-Forecast-Warnings",URLEncoder.encode(mapper.writeValueAsString(result.warnings()),StandardCharsets.UTF_8)).body(result.bytes());
  }
  /** Spring rejects oversized parts before the controller runs; answer with the same workbook error contract. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> uploadTooLarge(){
    var failure=tooLarge();var detail=ProblemDetail.forStatusAndDetail(failure.status(),failure.getMessage());
    detail.setProperty("code",failure.code());detail.setProperty("problems",failure.problems());
    return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore()).body(detail);
  }
  private static WorkbookFailure tooLarge(){
    return new WorkbookFailure("FORECAST_TEMPLATE_INVALID","Choose a workbook no larger than 5 MiB.",java.util.List.of(new WorkbookFailure.Problem("Workbook","","Maximum upload is 5 MiB.")));
  }
}
