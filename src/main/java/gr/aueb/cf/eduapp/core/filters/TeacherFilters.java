package gr.aueb.cf.eduapp.core.filters;


import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
@Builder

public class TeacherFilters {

    private UUID uuid;
    private String vat;
    private String amka;
    private String lastname;
    private boolean deleted;
    private String region;

}
