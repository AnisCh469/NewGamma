export interface Magasin {
  id: number;
  code: string;
  nom: string;
  localisation: string;
}

export interface Stock {
  id: number;
  magasinId: number;
  magasinCode: string;
  magasinNom: string;
  quantite: number;
  emplacement?: string;
  rayon?: string;
}

export interface MouvementDto {
  itemId: string;
  magasinId: number;
  typeMouvement: 'ENTREE' | 'SORTIE' | 'INVENTAIRE' | 'REFORME';
  quantite: number;
  referenceBon: string;
  motif: string;
  emplacement?: string;
  rayon?: string;
}
