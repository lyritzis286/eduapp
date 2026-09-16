package gr.aueb.cf.eduapp.service;

import gr.aueb.cf.eduapp.core.exceptions.EntityAlreadyExistsException;
import gr.aueb.cf.eduapp.dto.PersonalInfoInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherReadOnlyDTO;
import gr.aueb.cf.eduapp.dto.TeacherUpdateDTO;
import gr.aueb.cf.eduapp.dto.UserInsertDTO;
import gr.aueb.cf.eduapp.model.PersonalInfo;
import gr.aueb.cf.eduapp.model.Region;
import gr.aueb.cf.eduapp.model.Role;
import gr.aueb.cf.eduapp.model.Teacher;
import gr.aueb.cf.eduapp.model.User;
import gr.aueb.cf.eduapp.repository.RegionRepository;
import gr.aueb.cf.eduapp.repository.RoleRepository;
import gr.aueb.cf.eduapp.repository.TeacherRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the full Spring context (incl. method security) against a real MySQL
 * instance provisioned by Testcontainers, exercising {@link TeacherService}
 * through its repositories rather than mocks. Each test runs in its own
 * transaction, rolled back afterwards.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class TeacherServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    private static final long TEACHER_ROLE_ID = 3L;
    private static final long ATTICA_REGION_ID = 2L;

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveTeacher_persistsTeacherWithHashedPassword() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        TeacherInsertDTO dto = TeacherInsertDTO.builder()
                .firstname("John")
                .lastname("Doe")
                .vat(randomDigits(9))
                .regionId(ATTICA_REGION_ID)
                .userInsertDTO(UserInsertDTO.builder()
                        .username("jdoe_" + uniqueSuffix)
                        .password("Passw0rd!")
                        .roleId(TEACHER_ROLE_ID)
                        .build())
                .personalInfoInsertDTO(PersonalInfoInsertDTO.builder()
                        .amka(randomDigits(11))
                        .identityNumber("ID" + uniqueSuffix)
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        TeacherReadOnlyDTO result = teacherService.saveTeacher(dto);

        entityManager.flush();
        entityManager.clear();

        Teacher persisted = teacherRepository.findByVat(dto.vat()).orElseThrow();
        assertThat(persisted.getUuid().toString()).isEqualTo(result.uuid());
        assertThat(persisted.getFirstname()).isEqualTo("John");
        assertThat(persisted.getUser().getPassword()).isNotEqualTo("Passw0rd!");
        assertThat(persisted.getUser().getRole().getName()).isEqualTo("TEACHER");
        assertThat(persisted.getRegion().getId()).isEqualTo(ATTICA_REGION_ID);
    }

    @Test
    void saveTeacher_throwsEntityAlreadyExists_whenVatAlreadyPersisted() {
        Teacher existing = persistTeacher(UUID.randomUUID());
        long teacherCountBefore = teacherRepository.count();

        TeacherInsertDTO dto = TeacherInsertDTO.builder()
                .firstname("Jane")
                .lastname("Roe")
                .vat(existing.getVat())
                .regionId(ATTICA_REGION_ID)
                .userInsertDTO(UserInsertDTO.builder()
                        .username("newuser_" + UUID.randomUUID())
                        .password("Passw0rd!")
                        .roleId(TEACHER_ROLE_ID)
                        .build())
                .personalInfoInsertDTO(PersonalInfoInsertDTO.builder()
                        .amka(randomDigits(11))
                        .identityNumber("NEWID" + UUID.randomUUID())
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        assertThatThrownBy(() -> teacherService.saveTeacher(dto))
                .isInstanceOf(EntityAlreadyExistsException.class);

        assertThat(teacherRepository.count()).isEqualTo(teacherCountBefore);
    }

    @Test
    @WithMockUser(authorities = "EDIT_TEACHER")
    void updateTeacher_persistsChangesToDatabase() throws Exception {
        Teacher existing = persistTeacher(UUID.randomUUID());

        TeacherUpdateDTO dto = TeacherUpdateDTO.builder()
                .uuid(existing.getUuid())
                .firstname("Updated")
                .lastname("Lastname")
                .vat(existing.getVat())
                .regionId(existing.getRegion().getId())
                .userUpdateDTO(UserInsertDTO.builder()
                        .username(existing.getUser().getUsername())
                        .password("Passw0rd!")
                        .roleId(TEACHER_ROLE_ID)
                        .build())
                .personalInfoUpdateDTO(PersonalInfoInsertDTO.builder()
                        .amka(existing.getPersonalInfo().getAmka())
                        .identityNumber(existing.getPersonalInfo().getIdentityNumber())
                        .placeOfBirth("Athens")
                        .municipalityOfRegistration("Athens")
                        .build())
                .build();

        teacherService.updateTeacher(dto);

        entityManager.flush();
        entityManager.clear();

        Teacher reloaded = teacherRepository.findByUuid(existing.getUuid()).orElseThrow();
        assertThat(reloaded.getFirstname()).isEqualTo("Updated");
        assertThat(reloaded.getLastname()).isEqualTo("Lastname");
    }

    @Test
    @WithMockUser(authorities = "DELETE_TEACHER")
    void deleteTeacherByUUID_softDeletesTeacherPersonalInfoAndUserInDatabase() throws Exception {
        Teacher existing = persistTeacher(UUID.randomUUID());
        UUID uuid = existing.getUuid();

        teacherService.deleteTeacherByUUID(uuid);

        entityManager.flush();
        entityManager.clear();

        Optional<Teacher> stillFindableIgnoringDeleted = teacherRepository.findByUuid(uuid);
        assertThat(stillFindableIgnoringDeleted).isPresent();
        Teacher deleted = stillFindableIgnoringDeleted.get();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getPersonalInfo().isDeleted()).isTrue();
        assertThat(deleted.getUser().isDeleted()).isTrue();

        assertThat(teacherRepository.findByUuidAndDeletedFalse(uuid)).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "VIEW_TEACHERS")
    void getTeacherByUUID_deniesAccess_whenCallerLacksViewTeacherAuthority() {
        Teacher existing = persistTeacher(UUID.randomUUID());

        assertThatThrownBy(() -> teacherService.getTeacherByUUID(existing.getUuid()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Teacher persistTeacher(UUID marker) {
        Region region = regionRepository.findById(ATTICA_REGION_ID).orElseThrow();
        Role role = roleRepository.findById(TEACHER_ROLE_ID).orElseThrow();

        String suffix = marker.toString().substring(0, 8);

        User user = new User();
        user.setUsername("user_" + suffix);
        user.setPassword("hashed-secret");
        role.addUser(user);

        PersonalInfo personalInfo = new PersonalInfo();
        personalInfo.setAmka(randomDigits(11));
        personalInfo.setIdentityNumber("ID_" + suffix);
        personalInfo.setPlaceOfBirth("Athens");
        personalInfo.setMunicipalityOfRegistration("Athens");

        Teacher teacher = new Teacher();
        teacher.setFirstname("First_" + suffix);
        teacher.setLastname("Last_" + suffix);
        teacher.setVat(randomDigits(9));
        teacher.setPersonalInfo(personalInfo);
        teacher.addUser(user);
        region.addTeacher(teacher);

        teacherRepository.save(teacher);
        entityManager.flush();
        entityManager.clear();

        return teacherRepository.findByUuid(teacher.getUuid()).orElseThrow();
    }

    private static String randomDigits(int length) {
        long bound = (long) Math.pow(10, length);
        long value = ThreadLocalRandom.current().nextLong(bound);
        return String.format("%0" + length + "d", value);
    }
}
