package tn.defense.gamma3.reception.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.reception.domain.LigneReception;

import java.util.List;

@Repository
public interface LigneReceptionRepository extends JpaRepository<LigneReception, Long> {
    List<LigneReception> findByBpr_Id(Long bprId);
    List<LigneReception> findByItem_Id(java.util.UUID itemId);
}
