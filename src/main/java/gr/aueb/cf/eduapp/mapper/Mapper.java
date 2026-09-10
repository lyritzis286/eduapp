package gr.aueb.cf.eduapp.mapper;

import gr.aueb.cf.eduapp.dto.*;
import gr.aueb.cf.eduapp.model.PersonalInfo;
import gr.aueb.cf.eduapp.model.Teacher;
import gr.aueb.cf.eduapp.model.User;
import org.springframework.stereotype.Component;

@Component
public class Mapper {

    public Teacher mapToTeacherEntity(TeacherInsertDTO dto) {
        Teacher teacher = new Teacher();
        teacher.setFirstname(dto.firstname());
        teacher.setLastname(dto.lastname());
        teacher.setVat(dto.vat());

        UserInsertDTO userInsertDTO = dto.userInsertDTO();

        User user = new User();
        user.setUsername(userInsertDTO.username());
        user.setPassword(userInsertDTO.password());

        teacher.addUser(user);

        PersonalInfoInsertDTO personalInfoInsertDTO = dto.personalInfoInsertDTO();

        PersonalInfo personalInfo = new PersonalInfo();
        personalInfo.setAmka(personalInfoInsertDTO.amka());
        personalInfo.setIdentityNumber(personalInfoInsertDTO.identityNumber());
        personalInfo.setPlaceOfBirth(personalInfoInsertDTO.placeOfBirth());
        personalInfo.setMunicipalityOfRegistration(personalInfoInsertDTO.municipalityOfRegistration());

        teacher.setPersonalInfo(personalInfo);
        return teacher;
    }

    public TeacherReadOnlyDTO mapToTeacherReadOnlyDTO(Teacher teacher) {
        return new TeacherReadOnlyDTO(
                teacher.getUuid().toString() , teacher.getLastname(),
                teacher.getFirstname(),
                teacher.getVat(), teacher.getRegion().getName());

    }
}
