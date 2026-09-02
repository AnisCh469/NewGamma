package tn.defense.gamma3.catalogue.api;
 
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.catalogue.repository.ItemSpecification;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.distribution.repository.LigneDemandeRepository;
 
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
 
/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROTOCOLE DE DOCUMENTATION ÉDUCATIVE — Contrôleur Principal du Catalogue
 * Classe : ItemController
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE DU MODULE :
 * Ce contrôleur REST expose l'API de gestion du catalogue d'articles. Il gère la
 * pagination, le tri unifié sur la nomenclature compacte à 12 caractères, la
 * recherche multi-axe avec debouncing et l'arbitrage automatique des dotations.
 *
 * CONTEXTE TECHNIQUE & ARCHITECTURE :
 * 1. Pagination & Résolution N+1 : Récupération des articles via DTO léger pour
 *    éviter le chargement inutile de collections de documents volumineux.
 * 2. Jointure efficace : Calcul et agrégation en une seule fois des stocks actuels
 *    et réservations pour toute la page au lieu d'exécuter N requêtes individuelles.
 * 3. Robuste & Sûr : Prise en charge des types bruts renvoyés par la base de données
 *    avec conversion sécurisée (UUID, BigDecimal) pour immuniser contre les
 *    plantages ClassCastException lors du mapping de requêtes natives groupées.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/v1/items")
public class ItemController {
 
    private final ItemRepository itemRepository;
    private final StockRepository stockRepository;
    private final PlanArmementRepository planArmementRepository;
    private final LigneDemandeRepository ligneDemandeRepository;
 
    public ItemController(ItemRepository itemRepository, StockRepository stockRepository, PlanArmementRepository planArmementRepository, LigneDemandeRepository ligneDemandeRepository) {
        this.itemRepository = itemRepository;
        this.stockRepository = stockRepository;
        this.planArmementRepository = planArmementRepository;
        this.ligneDemandeRepository = ligneDemandeRepository;
    }
 
    /**
     * RÔLE :
     * Récupère la liste paginée et filtrée des articles avec leurs stocks et dotations.
     *
     * POURQUOI CETTE LOGIQUE :
     * - Assure la compatibilité avec l'UI standalone.
     * - Gère l'arbitrage en appliquant automatiquement le filtre par Plan d'Armement
     *   si l'utilisateur est un simple client d'unité (UNIT_USER).
     * - Optimise les performances en chargeant en mémoire des maps de stock/réservation
     *   globalement pour la page afin d'éviter le problème d'interrogation N+1.
     *
     * PARAMÈTRES :
     * @param page Numéro de la page demandée (0-indexed).
     * @param size Nombre d'articles par page (max 100).
     * @param sortBy Champ de tri principal (par défaut "nomenclature").
     * @param direction Direction du tri ("asc" ou "desc").
     * @param search Chaîne de recherche libre (désignation ou nomenclature).
     * @param typeConsommabilite Filtre optionnel de consommabilité (CONSOMMABLE ou NON_CONSOMMABLE).
     * @param bypassPlanArmement Si true, permet de forcer la lecture totale même pour un client (admin).
     *
     * RETOUR :
     * @return Un ResponseEntity contenant la liste mappée en DTO léger et les métadonnées de pagination.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "nomenclature") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String typeConsommabilite,
            @RequestParam(defaultValue = "false") boolean bypassPlanArmement,
            @RequestParam(required = false) Long magasinId
    ) {
        // ÉTAPE 1 : Encadrement préventif de la taille de page pour éviter les DOS
        size = Math.min(size, 100);
        
        // ÉTAPE 2 : Nettoyage et compatibilité du champ de tri (conversion des anciens champs fragmentés)
        if (sortBy.contains("classeCode") || sortBy.contains("itemCode")) {
            sortBy = "nomenclature";
        }
 
        // ÉTAPE 3 : Construction dynamique de l'ordre de tri stable (ajout de la nomenclature en tri secondaire si absent)
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy.split(",")).descending() : Sort.by(sortBy.split(",")).ascending();
        if (!sortBy.contains("nomenclature")) {
            sort = sort.and(Sort.by("nomenclature").ascending());
        }
 
        Pageable pageable = PageRequest.of(page, size, sort);
 
        Page<Item> pageResult;
        
        // ÉTAPE 4 : Initialisation de la spécification de recherche à null pour éviter IllegalArgumentException
        Specification<Item> spec = null;
        
        // ÉTAPE 5 : Application du filtre obligatoire lié au Plan d'Armement (si l'utilisateur est de rôle client)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User currentUser) {
            if (currentUser.getRole() == Role.UNIT_USER && !bypassPlanArmement) {
                spec = ItemSpecification.belongsToPlanArmement(currentUser.getMatricule());
            }
        }
        
        // ÉTAPE 6 : Enrichissement de la spécification par la recherche textuelle multi-axe et multi-token
        if (search != null && !search.isBlank()) {
            Specification<Item> searchSpec = ItemSpecification.searchByNomenclatureOrDesignation(search);
            spec = (spec == null) ? searchSpec : spec.and(searchSpec);
        }
        
        // ÉTAPE 7 : Enrichissement de la spécification par le type de consommabilité
        if (typeConsommabilite != null && !typeConsommabilite.isBlank()) {
            try {
                tn.defense.gamma3.catalogue.domain.TypeConsommabilite type = 
                    tn.defense.gamma3.catalogue.domain.TypeConsommabilite.valueOf(typeConsommabilite.toUpperCase());
                Specification<Item> typeSpec = ItemSpecification.hasTypeConsommabilite(type);
                spec = (spec == null) ? typeSpec : spec.and(typeSpec);
            } catch (IllegalArgumentException e) {
                // Ignorer si la valeur est incorrecte
            }
        }

        // ÉTAPE 7b : Enrichissement de la spécification par le magasin (si demandé)
        if (magasinId != null) {
            Specification<Item> magasinSpec = ItemSpecification.isInMagasin(magasinId);
            spec = (spec == null) ? magasinSpec : spec.and(magasinSpec);
        }
 
        // ÉTAPE 8 : Exécution de la requête paginée avec spécification dynamique
        pageResult = itemRepository.findAll(spec, pageable);
 
        // ÉTAPE 9 : Extraction sécurisée et robuste des identifiants et récupération groupée des stocks réels (Batch)
        List<UUID> itemIds = pageResult.getContent().stream().map(Item::getId).toList();
        List<Object[]> quantitiesData = (magasinId != null)
                ? stockRepository.getQuantitiesByItemIdsAndMagasinId(itemIds, magasinId)
                : stockRepository.getTotalQuantitiesByItemIds(itemIds);
        Map<UUID, BigDecimal> quantities = quantitiesData.stream()
                .collect(Collectors.toMap(
                        row -> {
                            if (row[0] instanceof UUID) return (UUID) row[0];
                            return UUID.fromString(row[0].toString());
                        },
                        row -> {
                            if (row[1] == null) return BigDecimal.ZERO;
                            if (row[1] instanceof BigDecimal) return (BigDecimal) row[1];
                            if (row[1] instanceof Number) return BigDecimal.valueOf(((Number) row[1]).doubleValue());
                            return new BigDecimal(row[1].toString());
                        }
                ));
 
        // ÉTAPE 10 : Récupération groupée et sécurisée des réservations actives (Batch)
        Map<UUID, BigDecimal> reservations = ligneDemandeRepository.getReservedQuantitiesByItemIds(itemIds).stream()
                .collect(Collectors.toMap(
                        row -> {
                            if (row[0] instanceof UUID) return (UUID) row[0];
                            return UUID.fromString(row[0].toString());
                        },
                        row -> {
                            if (row[1] == null) return BigDecimal.ZERO;
                            if (row[1] instanceof BigDecimal) return (BigDecimal) row[1];
                            if (row[1] instanceof Number) return BigDecimal.valueOf(((Number) row[1]).doubleValue());
                            return new BigDecimal(row[1].toString());
                        }
                ));
 
        // ÉTAPE 11 : Chargement des dotations de l'unité (Plan d'Armement) pour l'utilisateur connecté
        Map<UUID, PlanArmement> plansMap = new HashMap<>();
        if (auth != null && auth.getPrincipal() instanceof User currentUser && currentUser.getRole() == Role.UNIT_USER) {
            List<PlanArmement> plans = planArmementRepository.findByUniteCode(currentUser.getMatricule());
            for (PlanArmement p : plans) {
                plansMap.put(p.getItem().getId(), p);
            }
        }
 
        // ÉTAPE 12 : Mapping final vers la structure DTO légère unifiée
        List<ItemSummaryDto> dtos = pageResult.getContent().stream()
                .map(item -> {
                    PlanArmement plan = plansMap.get(item.getId());
                    return new ItemSummaryDto(
                        item.getId(),
                        item.getClasseCode(),
                        item.getSousClasseCode(),
                        item.getCategorieCode(),
                        item.getSerieCode(),
                        item.getItemCode(),
                        item.getNomenclature(),
                        item.getDesignation(),
                        item.getPrixUnitaire(),
                        item.getStockSecurite(),
                        item.getUniteGestionCode(),
                        item.getPhotoUrl(),
                        item.getDangerClass() != null ? item.getDangerClass().name() : null,
                        item.getTypeConsommabilite() != null ? item.getTypeConsommabilite().name() : "CONSOMMABLE",
                        quantities.getOrDefault(item.getId(), BigDecimal.ZERO),
                        reservations.getOrDefault(item.getId(), BigDecimal.ZERO),
                        plan != null ? plan.getQuantiteType() : null,
                        plan != null ? plan.getQuantiteReelle() : null
                    );
                })
                .toList();
 
        return ResponseEntity.ok(Map.of(
                "content", dtos,
                "totalElements", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages(),
                "currentPage", pageResult.getNumber(),
                "pageSize", pageResult.getSize()
        ));
    }
 
    /**
     * RÔLE :
     * Récupère un article unique par son identifiant technique (UUID).
     *
     * POURQUOI CETTE LOGIQUE :
     * Permet d'afficher la fiche détaillée complète de l'article avec sa liste de documents associés.
     *
     * PARAMÈTRES :
     * @param id L'identifiant UUID de l'article.
     *
     * RETOUR :
     * @return L'article complet s'il existe (200 OK), ou une erreur 404 Not Found si l'identifiant est inconnu.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable UUID id) {
        return itemRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
 
    /**
     * RÔLE :
     * Crée un nouvel article dans le catalogue.
     *
     * POURQUOI CETTE LOGIQUE :
     * Permet à l'administration d'enregistrer de nouvelles références de matériel.
     *
     * PARAMÈTRES :
     * @param item L'objet JSON décrivant l'article complet.
     *
     * RETOUR :
     * @return L'article persistant créé et son UUID généré automatiquement.
     */
    @PostMapping
    public ResponseEntity<Item> createItem(@RequestBody Item item) {
        Item savedItem = itemRepository.save(item);
        return ResponseEntity.ok(savedItem);
    }
}
