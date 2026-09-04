package gr.aueb.cf.eduapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@NoArgsConstructor
@Table(name = "attachments")
public class Attachment extends AbstractEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(name = "saved_name", nullable = false, unique = true)
    private String savedName;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String extension;
}
