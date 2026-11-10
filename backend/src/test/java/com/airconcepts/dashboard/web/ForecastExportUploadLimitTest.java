package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.forecast.ForecastAccess;
import com.airconcepts.dashboard.forecast.ForecastExportService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.ObjectMapper;

/** Standalone MVC: the controller's own upload-limit handler, without the Spring context or a real JWT decoder. */
class ForecastExportUploadLimitTest {
  private static final String XLSX = ForecastExportController.XLSX;
  private final ForecastExportService service = mock(ForecastExportService.class);
  /** Supplies the principal that the security chain would normally resolve. */
  private final HandlerMethodArgumentResolver principal = new HandlerMethodArgumentResolver() {
    @Override public boolean supportsParameter(MethodParameter parameter) {
      return Jwt.class.isAssignableFrom(parameter.getParameterType());
    }
    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
        NativeWebRequest request, WebDataBinderFactory binders) {
      return TestJwts.forSubject("export-test-user");
    }
  };
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new ForecastExportController(service, mock(CurrentUserResolver.class), mock(ForecastAccess.class), new ObjectMapper()))
      .setCustomArgumentResolvers(principal)
      .setControllerAdvice(new ApiFailureHandler())
      .build();
  private final UUID entity = UUID.randomUUID();

  /** Spring's multipart resolver rejects the part before the controller runs; the controller answers in the same contract. */
  @Test void frUc04_24_multipartLimitRejectionReturnsTheTemplateErrorContract() throws Exception {
    when(service.export(any(), any(), any(), any(), any()))
        .thenThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024));
    mvc.perform(multipart("/api/v1/entities/{id}/forecast/export", entity)
            .file(new MockMultipartFile("file", "big.xlsx", XLSX, new byte[] {1})))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("FORECAST_TEMPLATE_INVALID"))
        .andExpect(jsonPath("$.problems[0].sheet").value("Workbook"))
        .andExpect(jsonPath("$.problems[0].expected").value("Maximum upload is 5 MiB."));
  }

  @Test void frUc04_24_workbookAboveFiveMebibytesIsRejectedBeforeExport() throws Exception {
    mvc.perform(multipart("/api/v1/entities/{id}/forecast/export", entity)
            .file(new MockMultipartFile("file", "big.xlsx", XLSX, new byte[6 * 1024 * 1024])))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("FORECAST_TEMPLATE_INVALID"));
    verifyNoInteractions(service);
  }
}
