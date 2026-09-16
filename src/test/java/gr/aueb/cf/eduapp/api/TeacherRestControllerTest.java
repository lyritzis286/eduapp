package gr.aueb.cf.eduapp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.aueb.cf.eduapp.core.exceptions.EntityAlreadyExistsException;
import gr.aueb.cf.eduapp.core.exceptions.EntityInvalidArgumentException;
import gr.aueb.cf.eduapp.core.exceptions.EntityNotFoundException;
import gr.aueb.cf.eduapp.core.exceptions.FileUploadException;
import gr.aueb.cf.eduapp.dto.PersonalInfoInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherReadOnlyDTO;
import gr.aueb.cf.eduapp.dto.TeacherUpdateDTO;
import gr.aueb.cf.eduapp.dto.UserInsertDTO;
import gr.aueb.cf.eduapp.authentication.JwtService;
import gr.aueb.cf.eduapp.service.ITeacherService;
import gr.aueb.cf.eduapp.validator.TeacherInsertValidator;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.Errors;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests: only {@link TeacherRestController} and the web layer
 * are loaded, its service and validator collaborators are Mockito doubles
 * ({@link MockitoBean}). Security filters are disabled ({@code addFilters = false})
 * since URL-level authorization and method security live outside this slice and
 * are exercised elsewhere (service layer tests, full-context integration tests).
 */
@WebMvcTest(TeacherRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeacherRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Built directly rather than @Autowired: Boot 4's auto-configured ObjectMapper
    // bean is the new Jackson 3 (tools.jackson) type, not com.fasterxml.jackson's;
    // a plain instance is all these simple request DTOs need.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ITeacherService teacherService;

    @MockitoBean
    private TeacherInsertValidator teacherInsertValidator;

    // Not used directly: @WebMvcTest picks up JwtAuthenticationFilter (a Filter bean)
    // regardless of addFilters=false, so its constructor dependencies must resolve
    // for the context to start.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private TeacherInsertDTO.TeacherInsertDTOBuilder validInsertDtoBuilder() {
        return TeacherInsertDTO.builder()
                .firstname("John")
                .lastname("Doe")
                .vat("123456789")
                .regionId(1L)
                .userInsertDTO(UserInsertDTO.builder()
                        .username("jdoe")
                        .password("Passw0rd!")
                        .roleId(3L)
                        .build())
                .personalInfoInsertDTO(PersonalInfoInsertDTO.builder()
                        .amka("12345678901")
                        .identityNumber("AB123456")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build());
    }

    private TeacherUpdateDTO.TeacherUpdateDTOBuilder validUpdateDtoBuilder(UUID uuid) {
        return TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("John")
                .lastname("Doe")
                .vat("123456789")
                .regionId(1L)
                .userUpdateDTO(UserInsertDTO.builder()
                        .username("jdoe")
                        .password("Passw0rd!")
                        .roleId(3L)
                        .build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("12345678901")
                        .identityNumber("AB123456")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build());
    }

    // ---------- POST /api/v1/teachers ----------

    @Test
    void insertTeacher_returns201WithLocationHeader_onSuccess() throws Exception {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        UUID uuid = UUID.randomUUID();
        TeacherReadOnlyDTO readOnlyDTO = new TeacherReadOnlyDTO(uuid.toString(), "John", "Doe", "123456789", "Attica");

        when(teacherService.saveTeacher(any(TeacherInsertDTO.class))).thenReturn(readOnlyDTO);

        mockMvc.perform(post("/api/v1/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/teachers/" + uuid)))
                .andExpect(jsonPath("$.uuid").value(uuid.toString()))
                .andExpect(jsonPath("$.firstname").value("John"))
                .andExpect(jsonPath("$.vat").value("123456789"));
    }

    @Test
    void insertTeacher_returns400_whenRequestBodyFailsBeanValidation() throws Exception {
        TeacherInsertDTO dto = validInsertDtoBuilder().firstname(null).build();

        mockMvc.perform(post("/api/v1/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(teacherService, never()).saveTeacher(any());
    }

    @Test
    void insertTeacher_returns400_whenCustomValidatorRejectsVat() throws Exception {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();

        Answer<Void> rejectVat = invocation -> {
            Errors errors = invocation.getArgument(1);
            errors.rejectValue("vat", "teacher.vat.exists", "Teacher with vat= " + dto.vat() + " already exists");
            return null;
        };
        org.mockito.Mockito.doAnswer(rejectVat)
                .when(teacherInsertValidator)
                .validate(any(), any(Errors.class));

        mockMvc.perform(post("/api/v1/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.vat").exists());

        verify(teacherService, never()).saveTeacher(any());
    }

    @Test
    void insertTeacher_returns409_whenServiceReportsVatAlreadyExists() throws Exception {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        when(teacherService.saveTeacher(any(TeacherInsertDTO.class)))
                .thenThrow(new EntityAlreadyExistsException("Teacher", "Teacher with vat=123456789 already exists"));

        mockMvc.perform(post("/api/v1/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.description").value("Teacher with vat=123456789 already exists"));
    }

    @Test
    void insertTeacher_returns400_whenServiceReportsInvalidRegion() throws Exception {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        when(teacherService.saveTeacher(any(TeacherInsertDTO.class)))
                .thenThrow(new EntityInvalidArgumentException("Region", "Region with id=1 does not exist"));

        mockMvc.perform(post("/api/v1/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /api/v1/teachers/{uuid}/amka-file ----------

    @Test
    void uploadAmkaFile_returns204_onSuccess() throws Exception {
        UUID uuid = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "amkaFile", "amka.pdf", MediaType.APPLICATION_PDF_VALUE, "content".getBytes());

        mockMvc.perform(multipart("/api/v1/teachers/{uuid}/amka-file", uuid).file(file))
                .andExpect(status().isNoContent());

        verify(teacherService).saveAmkaFile(eq(uuid), any());
    }

    @Test
    void uploadAmkaFile_returns404_whenTeacherNotFound() throws Exception {
        UUID uuid = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "amkaFile", "amka.pdf", MediaType.APPLICATION_PDF_VALUE, "content".getBytes());

        org.mockito.Mockito.doThrow(new EntityNotFoundException("Teacher", "id=" + uuid))
                .when(teacherService).saveAmkaFile(eq(uuid), any());

        mockMvc.perform(multipart("/api/v1/teachers/{uuid}/amka-file", uuid).file(file))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadAmkaFile_returns500_whenFileUploadFails() throws Exception {
        UUID uuid = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "amkaFile", "amka.pdf", MediaType.APPLICATION_PDF_VALUE, "content".getBytes());

        org.mockito.Mockito.doThrow(new FileUploadException("FileUploadError", "Fail to detect file type"))
                .when(teacherService).saveAmkaFile(eq(uuid), any());

        mockMvc.perform(multipart("/api/v1/teachers/{uuid}/amka-file", uuid).file(file))
                .andExpect(status().isInternalServerError());
    }

    // ---------- PUT /api/v1/teachers/{uuid} ----------

    @Test
    void updateTeacher_returns200_onSuccess() throws Exception {
        UUID uuid = UUID.randomUUID();
        TeacherUpdateDTO dto = validUpdateDtoBuilder(uuid).build();
        TeacherReadOnlyDTO readOnlyDTO = new TeacherReadOnlyDTO(uuid.toString(), "John", "Doe", "123456789", "Attica");

        when(teacherService.updateTeacher(any(TeacherUpdateDTO.class))).thenReturn(readOnlyDTO);

        mockMvc.perform(put("/api/v1/teachers/{uuid}", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(uuid.toString()));
    }

    @Test
    void updateTeacher_returns404_whenTeacherNotFound() throws Exception {
        UUID uuid = UUID.randomUUID();
        TeacherUpdateDTO dto = validUpdateDtoBuilder(uuid).build();

        when(teacherService.updateTeacher(any(TeacherUpdateDTO.class)))
                .thenThrow(new EntityNotFoundException("Teacher", "id=" + uuid));

        mockMvc.perform(put("/api/v1/teachers/{uuid}", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTeacher_returns400_whenRequestBodyFailsBeanValidation() throws Exception {
        UUID uuid = UUID.randomUUID();
        TeacherUpdateDTO dto = validUpdateDtoBuilder(uuid).firstname(null).build();

        mockMvc.perform(put("/api/v1/teachers/{uuid}", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(teacherService, never()).updateTeacher(any());
    }

    // ---------- DELETE /api/v1/teachers/{uuid} ----------

    @Test
    void deleteTeacherByUUID_returns200_onSuccess() throws Exception {
        UUID uuid = UUID.randomUUID();
        TeacherReadOnlyDTO readOnlyDTO = new TeacherReadOnlyDTO(uuid.toString(), "John", "Doe", "123456789", "Attica");
        when(teacherService.deleteTeacherByUUID(uuid)).thenReturn(readOnlyDTO);

        mockMvc.perform(delete("/api/v1/teachers/{uuid}", uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(uuid.toString()));
    }

    @Test
    void deleteTeacherByUUID_returns404_whenTeacherNotFound() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(teacherService.deleteTeacherByUUID(uuid))
                .thenThrow(new EntityNotFoundException("Teacher", "id=" + uuid));

        mockMvc.perform(delete("/api/v1/teachers/{uuid}", uuid))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/v1/teachers/{uuid} ----------

    @Test
    void getTeacherByUUID_returns200_onSuccess() throws Exception {
        UUID uuid = UUID.randomUUID();
        TeacherReadOnlyDTO readOnlyDTO = new TeacherReadOnlyDTO(uuid.toString(), "John", "Doe", "123456789", "Attica");
        when(teacherService.getTeacherByUuidDeletedFalse(uuid)).thenReturn(readOnlyDTO);

        mockMvc.perform(get("/api/v1/teachers/{uuid}", uuid))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.firstname").value("John"))
                .andExpect(jsonPath("$.lastname").value("Doe"));
    }

    @Test
    void getTeacherByUUID_returns404_whenTeacherNotFound() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(teacherService.getTeacherByUuidDeletedFalse(uuid))
                .thenThrow(new EntityNotFoundException("Teacher", "id=" + uuid));

        mockMvc.perform(get("/api/v1/teachers/{uuid}", uuid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TeacherNotFound"));
    }

    // ---------- GET /api/v1/teachers ----------

    @Test
    void getFilteredAndPaginatedTeachers_returns200WithPageBody() throws Exception {
        TeacherReadOnlyDTO readOnlyDTO = new TeacherReadOnlyDTO(UUID.randomUUID().toString(), "John", "Doe", "123456789", "Attica");
        Page<TeacherReadOnlyDTO> page = new PageImpl<>(List.of(readOnlyDTO), PageRequest.of(0, 10), 1);

        when(teacherService.getTeachersPaginatedFiltered(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/teachers")
                        .param("page", "0")
                        .param("size", "10")
                        .param("vat", "123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].vat").value("123456789"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
