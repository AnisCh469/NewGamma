package tn.defense.gamma3.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.stock.api.dto.MouvementDto;
import tn.defense.gamma3.stock.api.dto.StockDto;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.domain.MouvementStock;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.domain.TypeMouvement;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;
import tn.defense.gamma3.stock.repository.StockRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final MagasinRepository magasinRepository;
    private final ItemRepository itemRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final UserRepository userRepository;

    public List<StockDto> getStockByItemId(java.util.UUID itemId) {
        return stockRepository.findByItem_Id(itemId).stream()
                .map(stock -> StockDto.builder()
                        .id(stock.getId())
                        .magasinId(stock.getMagasin().getId())
                        .magasinCode(stock.getMagasin().getCode())
                        .magasinNom(stock.getMagasin().getNom())
                        .quantite(stock.getQuantite())
                        .emplacement(stock.getEmplacement())
                        .rayon(stock.getRayon())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void effectuerMouvement(MouvementDto dto, String userMatricule) {
        Item item = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Article non trouvé"));
        Magasin magasin = magasinRepository.findById(dto.getMagasinId())
                .orElseThrow(() -> new IllegalArgumentException("Magasin non trouvé"));
        User user = userRepository.findByMatricule(userMatricule)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

        Stock stock = stockRepository.findByItem_IdAndMagasin_Id(item.getId(), magasin.getId())
                .orElseGet(() -> Stock.builder()
                        .item(item)
                        .magasin(magasin)
                        .quantite(BigDecimal.ZERO)
                        .emplacement(dto.getEmplacement())
                        .rayon(dto.getRayon())
                        .build());

        if (dto.getTypeMouvement() == TypeMouvement.ENTREE) {
            stock.setQuantite(stock.getQuantite().add(dto.getQuantite()));
            if (dto.getEmplacement() != null) {
                stock.setEmplacement(dto.getEmplacement());
            }
            if (dto.getRayon() != null) {
                stock.setRayon(dto.getRayon());
            }
        } else if (dto.getTypeMouvement() == TypeMouvement.SORTIE) {
            if (stock.getQuantite().compareTo(dto.getQuantite()) < 0) {
                throw new IllegalStateException("Stock insuffisant dans ce magasin");
            }
            stock.setQuantite(stock.getQuantite().subtract(dto.getQuantite()));
        } else {
            // Pour l'inventaire, on remplace la quantité
            stock.setQuantite(dto.getQuantite());
        }

        stockRepository.save(stock);

        MouvementStock mouvement = MouvementStock.builder()
                .referenceBon(dto.getReferenceBon())
                .typeMouvement(dto.getTypeMouvement())
                .item(item)
                .magasin(magasin)
                .quantite(dto.getQuantite())
                .dateMouvement(LocalDateTime.now())
                .motif(dto.getMotif())
                .creePar(user)
                .build();

        mouvementStockRepository.save(mouvement);
    }
}
