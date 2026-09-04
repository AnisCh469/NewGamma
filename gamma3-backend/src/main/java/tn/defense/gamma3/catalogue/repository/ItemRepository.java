package tn.defense.gamma3.catalogue.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.catalogue.domain.Item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour l'entité Item.
 *
 * Étend {@link JpaSpecificationExecutor} pour permettre la construction de requêtes
 * dynamiques via le pattern Specification (voir {@link ItemSpecification}).
 * C'est ce mécanisme qui alimente la recherche multi-token multi-axe
 * (désignation + code nomenclature composé).
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    /**
     * Recherche un article par sa nomenclature à 12 caractères.
     */
    Optional<Item> findByNomenclature(String nomenclature);

    /**
     * RÔLE :
     * Retourne, pour un préfixe de nomenclature déjà choisi (classe seule, classe+sous-classe,
     * ou classe+sous-classe+catégorie), les segments de 2 caractères distincts trouvés
     * immédiatement à la position "start" dans les nomenclatures déjà enregistrées en base,
     * avec un exemple de désignation et le nombre d'articles concernés.
     *
     * POURQUOI CETTE LOGIQUE :
     * Alimente les listes déroulantes en cascade du formulaire "Nouvel Article"
     * (Sous-classe > Catégorie > Série) avec les codes RÉELLEMENT déjà utilisés dans le
     * catalogue (63 000+ articles migrés de Gamma 2), conformément à la règle métier CCF
     * selon laquelle chaque article doit appartenir à une classification bien définie —
     * plutôt que de laisser un champ libre sujet aux fautes de frappe et aux doublons de sens
     * (ex : "MT" et "Mt" pour le même constructeur).
     *
     * PARAMÈTRES :
     * @param prefix le préfixe déjà choisi, longueur 0/2/4/6 (ex : "01", "0100", "0100MT")
     * @param start  position 1-based (SQL) du segment de 2 caractères à extraire
     *
     * RETOUR :
     * @return liste de lignes [code (2 caractères), désignation d'exemple, nombre d'articles]
     */
    @Query(value = "SELECT SUBSTRING(nomenclature, :start, 2) AS code, " +
            "MIN(designation) AS exemple, COUNT(*) AS nb " +
            "FROM items " +
            "WHERE nomenclature LIKE CONCAT(:prefix, '%') AND LENGTH(nomenclature) = 12 " +
            "GROUP BY SUBSTRING(nomenclature, :start, 2) " +
            "ORDER BY 1",
            nativeQuery = true)
    List<Object[]> findDistinctNomenclatureSegments(@Param("prefix") String prefix, @Param("start") int start);

    /**
     * RÔLE :
     * Calcule, pour une combinaison classe+sous-classe+catégorie+série déjà choisie, le plus
     * grand code Item (4 chiffres) déjà utilisé et le nombre d'articles existants — sert à
     * suggérer automatiquement le prochain code Item libre dans le formulaire de création,
     * afin d'éviter les collisions manuelles sur un même segment de nomenclature.
     *
     * PARAMÈTRES :
     * @param prefix le préfixe complet classe+sous-classe+catégorie+série (8 caractères)
     *
     * RETOUR :
     * @return une ligne unique [code Item maximal existant (ou null), nombre d'articles]
     */
    @Query(value = "SELECT MAX(SUBSTRING(nomenclature, 9, 4)) AS maxCode, COUNT(*) AS nb " +
            "FROM items " +
            "WHERE nomenclature LIKE CONCAT(:prefix, '%') AND LENGTH(nomenclature) = 12",
            nativeQuery = true)
    List<Object[]> findMaxItemCode(@Param("prefix") String prefix);

}

