package tn.defense.gamma3.distribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.distribution.domain.DemandeMateriel;
import tn.defense.gamma3.distribution.domain.StatutDemande;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeMaterielRepository extends JpaRepository<DemandeMateriel, Long> {

    Optional<DemandeMateriel> findByNumeroDemande(String numeroDemande);
    Optional<DemandeMateriel> findByBonSortieId(Long bonSortieId);

    // ─── Vue UNIT_USER ────────────────────────────────────────────────────────────
    // Retourne les demandes soumises par une unité spécifique.
    @Query("SELECT d FROM DemandeMateriel d JOIN FETCH d.unite LEFT JOIN FETCH d.bonSortie WHERE d.unite.id = :uniteId ORDER BY d.createdAt DESC")
    List<DemandeMateriel> findByUniteId(Long uniteId);

    // ─── Vue ADMIN (SGS) ──────────────────────────────────────────────────────────
    // Retourne TOUTES les demandes, tous magasins confondus.
    @Query("SELECT d FROM DemandeMateriel d JOIN FETCH d.unite LEFT JOIN FETCH d.bonSortie LEFT JOIN FETCH d.magasinDesigne ORDER BY d.createdAt DESC")
    List<DemandeMateriel> findAllWithUnite();

    // ─── Vue DA_MANAGER (Filtrée par magasin désigné) ─────────────────────────────
    // RÔLE : Un DA_MANAGER ne voit QUE les demandes routées vers SON magasin.
    //        Cela empêche un gestionnaire de SM1 de traiter des demandes de SM2.
    @Query("SELECT d FROM DemandeMateriel d JOIN FETCH d.unite LEFT JOIN FETCH d.bonSortie LEFT JOIN FETCH d.magasinDesigne WHERE d.magasinDesigne.id = :magasinId ORDER BY d.createdAt DESC")
    List<DemandeMateriel> findByMagasinDesigne_Id(@Param("magasinId") Long magasinId);

    // ─── Vue ADMIN : Escalades en attente de validation ──────────────────────────
    // Retourne les demandes en statut ESCALADE_SGS pour traitement prioritaire.
    @Query("SELECT d FROM DemandeMateriel d JOIN FETCH d.unite LEFT JOIN FETCH d.bonSortie LEFT JOIN FETCH d.magasinDesigne WHERE d.statut = :statut ORDER BY d.createdAt ASC")
    List<DemandeMateriel> findByStatut(@Param("statut") StatutDemande statut);

    // ─── Combinaison magasin + statut ─────────────────────────────────────────────
    @Query("SELECT d FROM DemandeMateriel d JOIN FETCH d.unite LEFT JOIN FETCH d.bonSortie WHERE d.magasinDesigne.id = :magasinId AND d.statut = :statut ORDER BY d.createdAt DESC")
    List<DemandeMateriel> findByMagasinDesigne_IdAndStatut(@Param("magasinId") Long magasinId, @Param("statut") StatutDemande statut);
}
