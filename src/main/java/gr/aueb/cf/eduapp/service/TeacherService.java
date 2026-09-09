package gr.aueb.cf.eduapp.service;

import gr.aueb.cf.eduapp.core.exceptions.EntityAlreadyExistsException;
import gr.aueb.cf.eduapp.core.exceptions.EntityInvalidArgumentException;
import gr.aueb.cf.eduapp.core.exceptions.EntityNotFoundException;
import gr.aueb.cf.eduapp.core.exceptions.FileUploadException;
import gr.aueb.cf.eduapp.dto.PersonalInfoInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherInsertDTO;
import gr.aueb.cf.eduapp.dto.TeacherReadOnlyDTO;
import gr.aueb.cf.eduapp.mapper.Mapper;
import gr.aueb.cf.eduapp.model.*;
import gr.aueb.cf.eduapp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherService implements ITeacherService{
    private final TeacherRepository teacherRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PersonalInfoRepository personalInfoRepository;
    private final Mapper mapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Override
    @Transactional(rollbackFor = {EntityAlreadyExistsException.class, EntityInvalidArgumentException.class})
    public TeacherReadOnlyDTO saveTeacher(TeacherInsertDTO dto)
            throws EntityAlreadyExistsException, EntityInvalidArgumentException {
        if (dto.vat() != null && teacherRepository.findByVat(dto.vat()).isPresent()) {
            throw new EntityAlreadyExistsException("Teacher", "Teacher with vat=" + dto.vat() + " already exists");
        }

        if (dto.personalInfoInsertDTO().amka() != null && personalInfoRepository.findByAmka(dto.personalInfoInsertDTO().amka()).isPresent()) {
            throw new EntityAlreadyExistsException("AMKA", "Teacher with amka=" + dto.personalInfoInsertDTO().amka() + " already exists");
        }

        if (dto.personalInfoInsertDTO().identityNumber() !=null && personalInfoRepository.findByIdentityNumber(dto.personalInfoInsertDTO().identityNumber()).isPresent()) {
            throw new EntityAlreadyExistsException("IdentityNumber", "Teacher with identityNumber=" + dto.personalInfoInsertDTO().identityNumber() + " already exists");
        }

        if (dto.userInsertDTO().username() != null && userRepository.findByUsername(dto.userInsertDTO().username()).isPresent()) {
            throw new EntityAlreadyExistsException("Username", "Teacher with username=" + dto.userInsertDTO().username() + " already exists");
        }

        Region region = regionRepository.findById(dto.regionId())
                .orElseThrow(() -> new EntityInvalidArgumentException
                        ("Region", "Region with id=" + dto.regionId() + " does not exist"));

        final Long teacherRoleId = 3L;  //TODO να αλλάξει το DTO να μην θέλει το roleid

        Role role = roleRepository.findById(teacherRoleId)
                .orElseThrow(() -> new EntityInvalidArgumentException
                        ("Role", "Role with id=" + teacherRoleId + " does not exist"));

        Teacher teacher = mapper.mapToTeacherEntity(dto);

        User user = teacher.getUser();
        user.setPassword(passwordEncoder.encode(dto.userInsertDTO().password()));

        region.addTeacher(teacher);
        role.addUser(user);

        teacherRepository.save(teacher);
        log.info("Teacher with vat={} saved successfully", dto.vat());
        return mapper.mapToTeacherReadOnlyDTO(teacher);

    }

    @Override
    @Retryable(
            includes = {IOException.class, HttpServerErrorException.class},
            maxRetries = 3,
            delay = 2000,
            multiplier = 2,
            maxDelay = 10000
    )
    @Transactional(rollbackFor= EntityNotFoundException.class)
    public void saveAmkaFile(UUID uuid, MultipartFile amkaFile)
            throws FileUploadException, EntityNotFoundException {
        Teacher teacher = teacherRepository.findByUuid(uuid)
                .orElseThrow(() -> new EntityNotFoundException("Teacher", "id=" + uuid));

        PersonalInfo personalInfo = teacher.getPersonalInfo();
        Path oldFilePath = personalInfo.getAmkaFile() != null ? Path.of(personalInfo.getAmkaFile().getFilePath()) : null;

        String originalFilename = amkaFile.getOriginalFilename();
        String savedName = UUID.randomUUID() + getFileExtension(originalFilename);
        Path newFilePath = Paths.get(uploadDir).resolve(savedName);

        Attachment attachment = new Attachment();
        attachment.setFileName(originalFilename);
        attachment.setSavedName(savedName);
        attachment.setFilePath(newFilePath.toString());
        attachment.setExtension(getFileExtension(originalFilename));

        try (InputStream is = amkaFile.getInputStream()) {
            Tika tika = new Tika();
            attachment.setContentType(tika.detect(is));
        }catch (IOException e) {
            throw new FileUploadException("FileUploadError","Fail to detect file type");
        }

        if (personalInfo.getAmkaFile() != null) {
            personalInfo.removeAmkaFile();
        }
        personalInfo.addAmkaFile(attachment);

        TransactionSynchronizationManager
                .registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            Files.createDirectories(newFilePath.getParent());
                            amkaFile.transferTo(newFilePath);

                            if(oldFilePath != null) {
                                Files.deleteIfExists(oldFilePath);
                            }

                            log.info("Amka file saved successfully for teacher with amka={}" ,personalInfo.getAmka());
                        }catch (IOException e) {
                            log.error("Critical: DB transaction committed but file upload failed for path={}", newFilePath, e);
                        }
                    }
                });


    }

    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return "";
    }
}
