package tn.defense.gamma3.catalogue.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.catalogue.domain.Item;

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

}

