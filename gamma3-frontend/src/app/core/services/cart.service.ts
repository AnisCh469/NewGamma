import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { PlanArmement } from './plan-armement.service';

export interface CartItem {
  plan: PlanArmement;
  quantiteDemandee: number;
}

/**
 * CartService — Service singleton gérant le panier de demande de matériel.
 * Utilise les Angular Signals pour une réactivité fine sans RxJS supplémentaire.
 * Le panier persiste tant que la session est ouverte.
 */
@Injectable({
  providedIn: 'root'
})
export class CartService {
  private _cart = signal<CartItem[]>([]);
  private _plans = signal<PlanArmement[]>([]);
  private _uniteId = signal<number | null>(null);

  // Signal calculé : lecture externe protégée
  cart = this._cart.asReadonly();
  plans = this._plans.asReadonly();

  totalCount = computed(() =>
    this._cart().reduce((sum, item) => sum + item.quantiteDemandee, 0)
  );

  totalLines = computed(() => this._cart().length);

  /**
   * Charge les plans d'armement de l'unité en session (pour les contrôles de quota).
   */
  setPlans(plans: PlanArmement[], uniteId: number): void {
    this._plans.set(plans);
    this._uniteId.set(uniteId);
  }

  getUniteId(): number | null {
    return this._uniteId();
  }

  /**
   * Calcule la dotation restante disponible pour un plan.
   * = dotation max (quantiteType) - quantité déjà en transit (quantiteVirtuelle)
   */
  getRemainingForPlan(plan: PlanArmement): number {
    return Math.max(0, plan.quantiteType - plan.quantiteVirtuelle);
  }

  /**
   * Retourne le plan correspondant à un itemId, ou null.
   */
  getPlanForItem(itemId: string): PlanArmement | null {
    return this._plans().find(p => p.item.id === itemId) ?? null;
  }

  /**
   * Ajoute un article au panier, ou incrémente la quantité si déjà présent.
   * Retourne un message d'erreur ou de succès.
   */
  addItem(plan: PlanArmement): { success: boolean; message: string } {
    const remaining = this.getRemainingForPlan(plan);

    if (remaining <= 0) {
      return {
        success: false,
        message: `Dotation maximale atteinte pour « ${plan.item.designation} ».`
      };
    }

    const existing = this._cart().find(c => c.plan.id === plan.id);
    if (existing) {
      if (existing.quantiteDemandee < remaining) {
        existing.quantiteDemandee += 1;
        this._cart.set([...this._cart()]);
        return {
          success: true,
          message: `Quantité passée à ${existing.quantiteDemandee} pour « ${plan.item.designation} ».`
        };
      } else {
        return {
          success: false,
          message: `Impossible de dépasser la dotation restante de ${remaining} u pour « ${plan.item.designation} ».`
        };
      }
    } else {
      this._cart.set([...this._cart(), { plan, quantiteDemandee: 1 }]);
      return {
        success: true,
        message: `« ${plan.item.designation} » ajouté au panier.`
      };
    }
  }

  removeItem(index: number): void {
    const updated = [...this._cart()];
    updated.splice(index, 1);
    this._cart.set(updated);
  }

  setQuantity(index: number, quantity: number): void {
    const updated = [...this._cart()];
    if (updated[index]) {
      updated[index] = { ...updated[index], quantiteDemandee: quantity };
      this._cart.set(updated);
    }
  }

  clearCart(): void {
    this._cart.set([]);
  }

  /**
   * Valide le panier : retourne une erreur si une quantité est invalide.
   */
  validate(): { valid: boolean; errors: string[] } {
    const errors: string[] = [];
    for (const c of this._cart()) {
      const remaining = this.getRemainingForPlan(c.plan);
      if (c.quantiteDemandee <= 0 || c.quantiteDemandee > remaining) {
        errors.push(`Quantité invalide pour « ${c.plan.item.designation} » (1 à ${remaining} u autorisé).`);
      }
    }
    return { valid: errors.length === 0, errors };
  }
}
