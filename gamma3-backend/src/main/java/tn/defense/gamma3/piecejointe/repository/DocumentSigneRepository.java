package tn.defense.gamma3.piecejointe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.defense.gamma3.piecejointe.domain.DocumentSigne;
import tn.defense.gamma3.piecejointe.domain.TypeDocumentSigne;

import java.util.List;

public interface DocumentSigneRepository extends JpaRepository<DocumentSigne, Long> {

    List<DocumentSigne> findByTypeDocumentAndReferenceIdOrderByDateUploadDesc(
            TypeDocumentSigne typeDocument, String referenceId);

    long countByTypeDocumentAndReferenceId(TypeDocumentSigne typeDocument, String referenceId);
}
