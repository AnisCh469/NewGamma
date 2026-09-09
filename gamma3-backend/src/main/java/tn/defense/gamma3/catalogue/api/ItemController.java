package tn.defense.gamma3.catalogue.api;
 
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
     * Code de l'unité d'un compte UNIT_USER, pour filtrer le catalogue sur son Plan d'Armement.
     * CORRECTIF (audit 2026-09-09) : on utilisait ici {@code currentUser.getMatricule()} en
     * supposant « matricule = code unité ». C'est vrai pour le Gestionnaire historique, mais
     * PAS pour le Commandant ({@code RPS_CDT} ≠ {@code RPS}) — résultat : un Commandant voyait
     * un catalogue VIDE. On privilégie désormais le rattachement explicite {@code User.unite}
     * (voir {@code FonctionUnite}), avec repli sur le matricule pour le compte générique
     * {@code client} sans unité.
     */
    private static String uniteCodeDe(User u) {
        return (u.getUnite() != null && u.getUnite().getCode() != null)
                ? u.getUnite().getCode()
                : u.getMatricule();
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
                spec = ItemSpecification.belongsToPlanArmement(uniteCodeDe(currentUser));
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
            List<PlanArmement> plans = planArmementRepository.findByUniteCode(uniteCodeDe(currentUser));
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
     * Alimente les listes déroulantes en cascade du formulaire "Nouvel Article"
     * (Sous-classe > Catégorie > Série > Code Article) avec les codes déjà utilisés dans
     * le catalogue existant, niveau par niveau.
     *
     * POURQUOI CETTE LOGIQUE :
     * Le référentiel de classification (classe/sous-classe/catégorie/série) n'est pas
     * modélisé comme des tables de référence séparées : il est directement encodé dans les
     * 12 caractères de `nomenclature` (voir Item.getClasseCode()/getSousClasseCode()/...).
     * Plutôt que de dupliquer un référentiel figé qui se périmerait, cet endpoint interroge
     * en direct les nomenclatures déjà enregistrées pour proposer les codes réellement en
     * usage, tout en laissant le champ éditable côté interface pour permettre l'ajout d'un
     * nouveau code (nouvel équipement jamais catalogué).
     *
     * PARAMÈTRES :
     * @param classe     code classe déjà choisi (2 caractères), ou absent pour le niveau racine
     * @param sousClasse code sous-classe déjà choisi (2 caractères)
     * @param categorie  code catégorie déjà choisi (2 caractères)
     * @param serie      code série déjà choisi (2 caractères)
     *
     * RETOUR :
     * @return si moins de 4 segments sont fournis : {"level": <nom>, "options": [{code,
     *         exempleDesignation, nombreArticles}, ...]} pour le niveau suivant à choisir ;
     *         si les 4 segments sont fournis : {"level": "item", "existingCount": <n>,
     *         "suggestedCode": <prochain code à 4 chiffres libre>}.
     *         400 si un segment fourni ne fait pas exactement 2 caractères.
     */
    @GetMapping("/nomenclature/next-level")
    public ResponseEntity<?> getNomenclatureNextLevel(
            @RequestParam(required = false) String classe,
            @RequestParam(required = false) String sousClasse,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) String serie) {

        StringBuilder prefix = new StringBuilder();
        int providedCount = 0;
        for (String segment : new String[]{classe, sousClasse, categorie, serie}) {
            if (segment == null || segment.isBlank()) {
                break;
            }
            String trimmed = segment.trim().toUpperCase();
            if (trimmed.length() != 2) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Chaque segment de nomenclature doit contenir exactement 2 caractères."));
            }
            prefix.append(trimmed);
            providedCount++;
        }

        if (providedCount == 4) {
            List<Object[]> rows = itemRepository.findMaxItemCode(prefix.toString());
            Object[] row = rows.isEmpty() ? new Object[]{null, 0L} : rows.get(0);
            String maxCode = (String) row[0];
            long existingCount = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            int nextNumber = (maxCode != null ? Integer.parseInt(maxCode) : 0) + 1;
            String suggestedCode = String.format("%04d", Math.min(nextNumber, 9999));
            return ResponseEntity.ok(Map.of(
                    "level", "item",
                    "existingCount", existingCount,
                    "suggestedCode", suggestedCode
            ));
        }

        int start = providedCount * 2 + 1; // position SQL 1-based du segment suivant
        List<Object[]> rows = itemRepository.findDistinctNomenclatureSegments(prefix.toString(), start);
        List<Map<String, Object>> options = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("code", r[0]);
            option.put("exempleDesignation", r[1]);
            option.put("nombreArticles", ((Number) r[2]).longValue());
            options.add(option);
        }

        String[] levels = {"classe", "sousClasse", "categorie", "serie"};
        return ResponseEntity.ok(Map.of("level", levels[providedCount], "options", options));
    }

    /**
     * RÔLE :
     * Crée un nouvel article dans le catalogue.
     *
     * POURQUOI CETTE LOGIQUE :
     * Permet à l'administration d'enregistrer de nouvelles références de matériel.
     * Une validation minimale est appliquée ici : la nomenclature est la clé
     * fonctionnelle de tout le système (recherche, jointures de stock, dotations
     * du Plan d'Armement) — une valeur absente ou mal formée casserait ces
     * fonctionnalités silencieusement, bien après la création, loin de l'écran où
     * l'erreur a été commise. Seuls les rôles autres que UNIT_USER (client d'unité)
     * peuvent créer un article, à l'image de la restriction déjà appliquée côté
     * interface (voir catalogue.component.html, bouton "Nouvel Article").
     *
     * PARAMÈTRES :
     * @param item L'objet JSON décrivant l'article complet.
     *
     * RETOUR :
     * @return 200 + l'article créé (avec son UUID généré) en cas de succès ;
     *         400 si la nomenclature ou la désignation sont absentes/invalides ;
     *         403 si l'utilisateur connecté est un client d'unité (UNIT_USER) ;
     *         409 si la nomenclature existe déjà (contrainte unique en base).
     */
    @PostMapping
    public ResponseEntity<?> createItem(@RequestBody Item item) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User currentUser && currentUser.getRole() == Role.UNIT_USER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Seule l'administration peut créer un article du catalogue."));
        }

        if (item.getNomenclature() == null || item.getNomenclature().trim().length() != 12) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La nomenclature est obligatoire et doit contenir exactement 12 caractères."));
        }
        if (item.getDesignation() == null || item.getDesignation().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La désignation est obligatoire."));
        }
        item.setNomenclature(item.getNomenclature().trim());

        // Un nouvel article n'a jamais d'identifiant, de documents ni d'horodatage fournis
        // par le client : ces champs sont exclusivement gérés par le serveur (UUID généré
        // par @GeneratedValue, horodatages par @CreationTimestamp / @UpdateTimestamp).
        item.setId(null);
        item.setDocuments(new ArrayList<>());

        try {
            Item savedItem = itemRepository.save(item);
            return ResponseEntity.ok(savedItem);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Un article avec la nomenclature '" + item.getNomenclature() + "' existe déjà."));
        }
    }
}
