package gr.aueb.cf.eduapp.service;

import gr.aueb.cf.eduapp.core.exceptions.EntityAlreadyExistsException;
import gr.aueb.cf.eduapp.core.exceptions.EntityInvalidArgumentException;
import gr.aueb.cf.eduapp.core.exceptions.EntityNotFoundException;
import gr.aueb.cf.eduapp.core.exceptions.FileUploadException;
import gr.aueb.cf.eduapp.core.filters.TeacherFilters;
import gr.aueb.cf.eduapp.dto.PersonalInfoInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherReadOnlyDTO;
import gr.aueb.cf.eduapp.dto.TeacherUpdateDTO;
import gr.aueb.cf.eduapp.dto.UserInsertDTO;
import gr.aueb.cf.eduapp.mapper.Mapper;
import gr.aueb.cf.eduapp.model.Attachment;
import gr.aueb.cf.eduapp.model.PersonalInfo;
import gr.aueb.cf.eduapp.model.Region;
import gr.aueb.cf.eduapp.model.Role;
import gr.aueb.cf.eduapp.model.Teacher;
import gr.aueb.cf.eduapp.model.User;
import gr.aueb.cf.eduapp.repository.PersonalInfoRepository;
import gr.aueb.cf.eduapp.repository.RegionRepository;
import gr.aueb.cf.eduapp.repository.RoleRepository;
import gr.aueb.cf.eduapp.repository.TeacherRepository;
import gr.aueb.cf.eduapp.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PersonalInfoRepository personalInfoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private TeacherService teacherService;

    @BeforeEach
    void setUp() {
        teacherService = new TeacherService(
                teacherRepository,
                regionRepository,
                userRepository,
                roleRepository,
                personalInfoRepository,
                new Mapper(),
                passwordEncoder
        );
    }

    // ---------- helpers ----------

    private Region buildRegion(long id, String name) {
        Region region = new Region();
        region.setId(id);
        region.setName(name);
        return region;
    }

    private Role buildRole(long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }

    private Teacher buildTeacher(UUID uuid, String vat, String identityNumber, String username, Region region) {
        Teacher teacher = new Teacher();
        teacher.setUuid(uuid);
        teacher.setFirstname("Old");
        teacher.setLastname("Name");
        teacher.setVat(vat);

        PersonalInfo personalInfo = new PersonalInfo();
        personalInfo.setAmka("11111111111");
        personalInfo.setIdentityNumber(identityNumber);
        personalInfo.setPlaceOfBirth("Athens");
        personalInfo.setMunicipalityOfRegistration("Athens");
        teacher.setPersonalInfo(personalInfo);

        User user = new User();
        user.setUsername(username);
        user.setPassword("hashed");
        teacher.addUser(user);

        region.addTeacher(teacher);

        return teacher;
    }

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

    // ---------- saveTeacher ----------

    @Test
    void saveTeacher_success_savesTeacherWithEncodedPasswordAndReturnsDto() throws Exception {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        Region region = buildRegion(1L, "Attica");
        Role role = buildRole(3L, "TEACHER");

        when(teacherRepository.findByVatAndDeletedFalse("123456789")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByAmka("12345678901")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByIdentityNumber("AB123456")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.empty());
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashedPwd");

        TeacherReadOnlyDTO result = teacherService.saveTeacher(dto);

        assertThat(result.firstname()).isEqualTo("John");
        assertThat(result.lastname()).isEqualTo("Doe");
        assertThat(result.vat()).isEqualTo("123456789");
        assertThat(result.region()).isEqualTo("Attica");

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        Teacher saved = captor.getValue();
        assertThat(saved.getUser().getPassword()).isEqualTo("hashedPwd");
        assertThat(saved.getRegion()).isEqualTo(region);
        assertThat(saved.getUser().getRole()).isEqualTo(role);
    }

    @Test
    void saveTeacher_throwsEntityAlreadyExists_whenVatAlreadyExists() {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        when(teacherRepository.findByVatAndDeletedFalse("123456789"))
                .thenReturn(Optional.of(new Teacher()));

        assertThatThrownBy(() -> teacherService.saveTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class)
                .hasMessageContaining("vat=123456789");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    void saveTeacher_throwsEntityAlreadyExists_whenAmkaAlreadyExists() {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        when(teacherRepository.findByVatAndDeletedFalse("123456789")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByAmka("12345678901")).thenReturn(Optional.of(new PersonalInfo()));

        assertThatThrownBy(() -> teacherService.saveTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class)
                .hasMessageContaining("amka=12345678901");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    void saveTeacher_throwsEntityAlreadyExists_whenIdentityNumberAlreadyExists() {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        when(teacherRepository.findByVatAndDeletedFalse("123456789")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByAmka("12345678901")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByIdentityNumber("AB123456")).thenReturn(Optional.of(new PersonalInfo()));

        assertThatThrownBy(() -> teacherService.saveTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class)
                .hasMessageContaining("identityNumber=AB123456");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    void saveTeacher_throwsEntityAlreadyExists_whenUsernameAlreadyExists() {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        when(teacherRepository.findByVatAndDeletedFalse("123456789")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByAmka("12345678901")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByIdentityNumber("AB123456")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> teacherService.saveTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class)
                .hasMessageContaining("username=jdoe");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    void saveTeacher_throwsEntityInvalidArgument_whenRegionDoesNotExist() {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        when(teacherRepository.findByVatAndDeletedFalse("123456789")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByAmka("12345678901")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByIdentityNumber("AB123456")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.empty());
        when(regionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.saveTeacher(dto))
                .isInstanceOf(EntityInvalidArgumentException.class)
                .hasMessageContaining("Region with id=1");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    void saveTeacher_throwsEntityInvalidArgument_whenTeacherRoleDoesNotExist() {
        TeacherInsertDTO dto = validInsertDtoBuilder().build();
        Region region = buildRegion(1L, "Attica");
        when(teacherRepository.findByVatAndDeletedFalse("123456789")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByAmka("12345678901")).thenReturn(Optional.empty());
        when(personalInfoRepository.findByIdentityNumber("AB123456")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.empty());
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region));
        when(roleRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.saveTeacher(dto))
                .isInstanceOf(EntityInvalidArgumentException.class)
                .hasMessageContaining("Role with id=3");

        verify(teacherRepository, never()).save(any());
    }

    // ---------- updateTeacher ----------

    @Test
    void updateTeacher_success_updatesNameAndReturnsDto() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);

        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("New")
                .lastname("Name2")
                .vat("111111111")
                .regionId(1L)
                .userUpdateDTO(UserInsertDTO.builder().username("olduser").password("Passw0rd!").roleId(3L).build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("11111111111")
                        .identityNumber("ID1")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));

        TeacherReadOnlyDTO result = teacherService.updateTeacher(dto);

        assertThat(result.firstname()).isEqualTo("New");
        assertThat(result.lastname()).isEqualTo("Name2");
        verify(teacherRepository).save(teacher);
    }

    @Test
    void updateTeacher_throwsEntityNotFound_whenTeacherDoesNotExist() {
        UUID uuid = UUID.randomUUID();
        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("New")
                .lastname("Name2")
                .vat("111111111")
                .regionId(1L)
                .userUpdateDTO(UserInsertDTO.builder().username("olduser").password("Passw0rd!").roleId(3L).build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("11111111111")
                        .identityNumber("ID1")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();
        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.updateTeacher(dto))
                .isInstanceOf(EntityNotFoundException.class);

        verify(teacherRepository, never()).save(any());
    }

    @Test
    void updateTeacher_throwsEntityAlreadyExists_whenNewVatConflicts() {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);

        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("New")
                .lastname("Name2")
                .vat("999999999")
                .regionId(1L)
                .userUpdateDTO(UserInsertDTO.builder().username("olduser").password("Passw0rd!").roleId(3L).build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("11111111111")
                        .identityNumber("ID1")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));
        when(teacherRepository.findByVat("999999999")).thenReturn(Optional.of(new Teacher()));

        assertThatThrownBy(() -> teacherService.updateTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class);

        assertThat(teacher.getVat()).isEqualTo("111111111");
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void updateTeacher_throwsEntityAlreadyExists_whenNewIdentityNumberConflicts() {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);

        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("New")
                .lastname("Name2")
                .vat("111111111")
                .regionId(1L)
                .userUpdateDTO(UserInsertDTO.builder().username("olduser").password("Passw0rd!").roleId(3L).build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("11111111111")
                        .identityNumber("ID2")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));
        when(personalInfoRepository.findByIdentityNumber("ID2")).thenReturn(Optional.of(new PersonalInfo()));

        assertThatThrownBy(() -> teacherService.updateTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class);

        assertThat(teacher.getPersonalInfo().getIdentityNumber()).isEqualTo("ID1");
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void updateTeacher_movesTeacherToNewRegion_whenRegionIdChanges() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region oldRegion = buildRegion(1L, "Attica");
        Region newRegion = buildRegion(2L, "Thessaly");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", oldRegion);

        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("New")
                .lastname("Name2")
                .vat("111111111")
                .regionId(2L)
                .userUpdateDTO(UserInsertDTO.builder().username("olduser").password("Passw0rd!").roleId(3L).build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("11111111111")
                        .identityNumber("ID1")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(newRegion));

        TeacherReadOnlyDTO result = teacherService.updateTeacher(dto);

        assertThat(teacher.getRegion()).isEqualTo(newRegion);
        assertThat(result.region()).isEqualTo("Thessaly");
    }

    @Test
    void updateTeacher_throwsEntityInvalidArgument_whenNewRegionDoesNotExist() {
        UUID uuid = UUID.randomUUID();
        Region oldRegion = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", oldRegion);

        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("New")
                .lastname("Name2")
                .vat("111111111")
                .regionId(2L)
                .userUpdateDTO(UserInsertDTO.builder().username("olduser").password("Passw0rd!").roleId(3L).build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("11111111111")
                        .identityNumber("ID1")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));
        when(regionRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.updateTeacher(dto))
                .isInstanceOf(EntityInvalidArgumentException.class);

        verify(teacherRepository, never()).save(any());
    }

    @Test
    void updateTeacher_throwsEntityAlreadyExists_whenNewUsernameConflicts() {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);

        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(uuid)
                .firstname("New")
                .lastname("Name2")
                .vat("111111111")
                .regionId(1L)
                .userUpdateDTO(UserInsertDTO.builder().username("newuser").password("Passw0rd!").roleId(3L).build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka("11111111111")
                        .identityNumber("ID1")
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> teacherService.updateTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class);

        assertThat(teacher.getUser().getUsername()).isEqualTo("olduser");
        verify(teacherRepository, never()).save(any());
    }

    // ---------- deleteTeacherByUUID ----------

    @Test
    void deleteTeacherByUUID_success_softDeletesTeacherPersonalInfoAndUser() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);

        when(teacherRepository.findByUuidAndDeletedFalse(uuid)).thenReturn(Optional.of(teacher));

        TeacherReadOnlyDTO result = teacherService.deleteTeacherByUUID(uuid);

        assertThat(teacher.isDeleted()).isTrue();
        assertThat(teacher.getPersonalInfo().isDeleted()).isTrue();
        assertThat(teacher.getUser().isDeleted()).isTrue();
        assertThat(result.uuid()).isEqualTo(uuid.toString());
    }

    @Test
    void deleteTeacherByUUID_throwsEntityNotFound_whenTeacherDoesNotExistOrAlreadyDeleted() {
        UUID uuid = UUID.randomUUID();
        when(teacherRepository.findByUuidAndDeletedFalse(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.deleteTeacherByUUID(uuid))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- getTeacherByUUID ----------

    @Test
    void getTeacherByUUID_success_returnsDto() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);
        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));

        TeacherReadOnlyDTO result = teacherService.getTeacherByUUID(uuid);

        assertThat(result.uuid()).isEqualTo(uuid.toString());
        assertThat(result.region()).isEqualTo("Attica");
    }

    @Test
    void getTeacherByUUID_throwsEntityNotFound_whenTeacherDoesNotExist() {
        UUID uuid = UUID.randomUUID();
        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.getTeacherByUUID(uuid))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- getTeacherByUuidDeletedFalse ----------

    @Test
    void getTeacherByUuidDeletedFalse_success_returnsDto() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);
        when(teacherRepository.findByUuidAndDeletedFalse(uuid)).thenReturn(Optional.of(teacher));

        TeacherReadOnlyDTO result = teacherService.getTeacherByUuidDeletedFalse(uuid);

        assertThat(result.uuid()).isEqualTo(uuid.toString());
    }

    @Test
    void getTeacherByUuidDeletedFalse_throwsEntityNotFound_whenTeacherDeletedOrMissing() {
        UUID uuid = UUID.randomUUID();
        when(teacherRepository.findByUuidAndDeletedFalse(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.getTeacherByUuidDeletedFalse(uuid))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- getPaginatedTeachers / getPaginatedTeachersDeletedFalse ----------

    @Test
    void getPaginatedTeachers_returnsMappedPage() {
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(UUID.randomUUID(), "111111111", "ID1", "olduser", region);
        Pageable pageable = PageRequest.of(0, 10);
        when(teacherRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(teacher), pageable, 1));

        Page<TeacherReadOnlyDTO> result = teacherService.getPaginatedTeachers(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().vat()).isEqualTo("111111111");
    }

    @Test
    void getPaginatedTeachersDeletedFalse_returnsMappedPage() {
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(UUID.randomUUID(), "111111111", "ID1", "olduser", region);
        Pageable pageable = PageRequest.of(0, 10);
        when(teacherRepository.findAllByDeletedFalse(pageable)).thenReturn(new PageImpl<>(List.of(teacher), pageable, 1));

        Page<TeacherReadOnlyDTO> result = teacherService.getPaginatedTeachersDeletedFalse(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ---------- getTeachersPaginatedFiltered ----------

    @Test
    void getTeachersPaginatedFiltered_byUuid_returnsSingleResultPage() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);
        Pageable pageable = PageRequest.of(0, 10);
        TeacherFilters filters = TeacherFilters.builder().uuid(uuid).build();

        when(teacherRepository.findByUuidAndDeletedFalse(uuid)).thenReturn(Optional.of(teacher));

        Page<TeacherReadOnlyDTO> result = teacherService.getTeachersPaginatedFiltered(pageable, filters);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().uuid()).isEqualTo(uuid.toString());
        verify(teacherRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getTeachersPaginatedFiltered_byUuid_throwsEntityNotFound_whenMissing() {
        UUID uuid = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        TeacherFilters filters = TeacherFilters.builder().uuid(uuid).build();
        when(teacherRepository.findByUuidAndDeletedFalse(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.getTeachersPaginatedFiltered(pageable, filters))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getTeachersPaginatedFiltered_byAmka_returnsSingleResultPage() throws Exception {
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(UUID.randomUUID(), "111111111", "ID1", "olduser", region);
        Pageable pageable = PageRequest.of(0, 10);
        TeacherFilters filters = TeacherFilters.builder().amka("11111111111").build();

        when(teacherRepository.findByPersonalInfo_Amka("11111111111")).thenReturn(Optional.of(teacher));

        Page<TeacherReadOnlyDTO> result = teacherService.getTeachersPaginatedFiltered(pageable, filters);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getTeachersPaginatedFiltered_byAmka_throwsEntityNotFound_whenMissing() {
        Pageable pageable = PageRequest.of(0, 10);
        TeacherFilters filters = TeacherFilters.builder().amka("00000000000").build();
        when(teacherRepository.findByPersonalInfo_Amka("00000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.getTeachersPaginatedFiltered(pageable, filters))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getTeachersPaginatedFiltered_byVat_returnsSingleResultPage() throws Exception {
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(UUID.randomUUID(), "111111111", "ID1", "olduser", region);
        Pageable pageable = PageRequest.of(0, 10);
        TeacherFilters filters = TeacherFilters.builder().vat("111111111").build();

        when(teacherRepository.findByVatAndDeletedFalse("111111111")).thenReturn(Optional.of(teacher));

        Page<TeacherReadOnlyDTO> result = teacherService.getTeachersPaginatedFiltered(pageable, filters);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getTeachersPaginatedFiltered_byVat_throwsEntityNotFound_whenMissing() {
        Pageable pageable = PageRequest.of(0, 10);
        TeacherFilters filters = TeacherFilters.builder().vat("000000000").build();
        when(teacherRepository.findByVatAndDeletedFalse("000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.getTeachersPaginatedFiltered(pageable, filters))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getTeachersPaginatedFiltered_noDirectFilters_fallsBackToSpecification() throws Exception {
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(UUID.randomUUID(), "111111111", "ID1", "olduser", region);
        Pageable pageable = PageRequest.of(0, 10);
        TeacherFilters filters = TeacherFilters.builder().lastname("Name").build();

        when(teacherRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(teacher), pageable, 1));

        Page<TeacherReadOnlyDTO> result = teacherService.getTeachersPaginatedFiltered(pageable, filters);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(teacherRepository, never()).findByUuidAndDeletedFalse(any());
        verify(teacherRepository, never()).findByPersonalInfo_Amka(any());
        verify(teacherRepository, never()).findByVatAndDeletedFalse(any());
    }

    // ---------- isTeacherExistsByVat ----------

    @Test
    void isTeacherExistsByVat_returnsTrue_whenPresent() {
        when(teacherRepository.findByVatAndDeletedFalse("111111111")).thenReturn(Optional.of(new Teacher()));

        assertThat(teacherService.isTeacherExistsByVat("111111111")).isTrue();
    }

    @Test
    void isTeacherExistsByVat_returnsFalse_whenAbsent() {
        when(teacherRepository.findByVatAndDeletedFalse("111111111")).thenReturn(Optional.empty());

        assertThat(teacherService.isTeacherExistsByVat("111111111")).isFalse();
    }

    // ---------- saveAmkaFile ----------

    @TempDir
    Path tempDir;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void saveAmkaFile_success_attachesFileAndWritesItAfterCommit() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);
        ReflectionTestUtils.setField(teacherService, "uploadDir", tempDir.toString());

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));

        MockMultipartFile multipartFile = new MockMultipartFile(
                "amkaFile", "amka.pdf", "application/pdf", "dummy-pdf-content".getBytes());

        TransactionSynchronizationManager.initSynchronization();
        try {
            teacherService.saveAmkaFile(uuid, multipartFile);

            Attachment attachment = teacher.getPersonalInfo().getAmkaFile();
            assertThat(attachment).isNotNull();
            assertThat(attachment.getFileName()).isEqualTo("amka.pdf");
            assertThat(attachment.getExtension()).isEqualTo(".pdf");
            assertThat(attachment.getContentType()).isNotBlank();

            runAfterCommitSynchronizations();

            Path writtenFile = tempDir.resolve(attachment.getSavedName());
            assertThat(Files.exists(writtenFile)).isTrue();
            assertThat(Files.readString(writtenFile)).isEqualTo("dummy-pdf-content");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void saveAmkaFile_replacesPreviousFile_deletesOldFileAfterCommit() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);
        ReflectionTestUtils.setField(teacherService, "uploadDir", tempDir.toString());

        Path oldFilePath = tempDir.resolve("old-attachment.pdf");
        Files.writeString(oldFilePath, "old-content");
        Attachment oldAttachment = new Attachment();
        oldAttachment.setFileName("old.pdf");
        oldAttachment.setSavedName("old-attachment.pdf");
        oldAttachment.setFilePath(oldFilePath.toString());
        oldAttachment.setContentType("application/pdf");
        oldAttachment.setExtension(".pdf");
        teacher.getPersonalInfo().addAmkaFile(oldAttachment);

        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));

        MockMultipartFile multipartFile = new MockMultipartFile(
                "amkaFile", "new.pdf", "application/pdf", "new-content".getBytes());

        TransactionSynchronizationManager.initSynchronization();
        try {
            teacherService.saveAmkaFile(uuid, multipartFile);

            Attachment newAttachment = teacher.getPersonalInfo().getAmkaFile();
            assertThat(newAttachment).isNotEqualTo(oldAttachment);

            runAfterCommitSynchronizations();

            assertThat(Files.exists(oldFilePath)).isFalse();
            Path newFilePath = tempDir.resolve(newAttachment.getSavedName());
            assertThat(Files.exists(newFilePath)).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void saveAmkaFile_throwsEntityNotFound_whenTeacherDoesNotExist() {
        UUID uuid = UUID.randomUUID();
        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        MockMultipartFile multipartFile = new MockMultipartFile(
                "amkaFile", "amka.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> teacherService.saveAmkaFile(uuid, multipartFile))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void saveAmkaFile_throwsFileUploadException_whenContentTypeDetectionFails() throws Exception {
        UUID uuid = UUID.randomUUID();
        Region region = buildRegion(1L, "Attica");
        Teacher teacher = buildTeacher(uuid, "111111111", "ID1", "olduser", region);
        ReflectionTestUtils.setField(teacherService, "uploadDir", tempDir.toString());
        when(teacherRepository.findByUuid(uuid)).thenReturn(Optional.of(teacher));

        MultipartFile brokenFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(brokenFile.getOriginalFilename()).thenReturn("amka.pdf");
        when(brokenFile.getInputStream()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> teacherService.saveAmkaFile(uuid, brokenFile))
                .isInstanceOf(FileUploadException.class);
    }

    private void runAfterCommitSynchronizations() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCommit();
        }
    }
}
