import { Component, inject, effect, HostListener } from '@angular/core';
import { RouterOutlet, RouterModule, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth/auth.service';
import { NotificationService } from './core/services/notification.service';
import { UniteService } from './core/services/unite.service';
import { InactivityService } from './core/services/inactivity.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'gamma3-frontend';
  authService = inject(AuthService);
  notificationService = inject(NotificationService);
  uniteService = inject(UniteService);
  private inactivityService = inject(InactivityService);
  router = inject(Router);

  isDrawerOpen = false;
  unitId: number | null = null;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.isDrawerOpen) {
      this.closeDrawer();
    }
  }

  /**
   * Fermeture de l'onglet ou du navigateur : notifie le serveur (best-effort, voir
   * AuthService.notifierFermeture) pour la traçabilité. La fin de session elle-même est déjà
   * garantie par sessionStorage, qui s'efface automatiquement — pas besoin d'attendre cet appel.
   */
  @HostListener('window:beforeunload')
  onBeforeUnload(): void {
    if (this.authService.isAuthenticated()) {
      this.authService.notifierFermeture();
    }
  }

  constructor() {
    // Reactively start or stop polling based on the user's logged-in state
    effect(() => {
      const user = this.authService.currentUser();
      if (user) {
        this.notificationService.startPolling();
        this.inactivityService.demarrer();

        // Rattachement Unité explicite (voir AuthResponse.uniteId côté backend, depuis le
        // 2026-09-04) : ne plus déduire l'unité du compte via son matricule (Gestionnaire ET
        // Commandant d'une même unité ont désormais des matricules différents et libres).
        if (user.role === 'UNIT_USER' && user.uniteId) {
          this.unitId = user.uniteId;
        }
      } else {
        this.notificationService.stopPolling();
        this.inactivityService.arreter();
        this.isDrawerOpen = false;
        this.unitId = null;
      }
    });
  }

  toggleDrawer(event: Event): void {
    event.stopPropagation();
    this.isDrawerOpen = !this.isDrawerOpen;
    if (this.isDrawerOpen) {
      this.notificationService.fetchNotifications();
    }
  }

  closeDrawer(): void {
    this.isDrawerOpen = false;
  }

  markAsRead(event: Event, id: number): void {
    event.stopPropagation();
    this.notificationService.markAsRead(id);
  }

  handleNotificationClick(event: Event, notif: any): void {
    event.stopPropagation();
    this.notificationService.markAsRead(notif.id);
    this.isDrawerOpen = false;

    // Smart routing based on notification type and user role
    const role = this.authService.currentUser()?.role;
    const isAdmin = role === 'SUPER_ADMIN' || role === 'ADMIN' || role === 'DA_MANAGER';
    const refId = notif.referenceId;

    if (notif.type === 'DOTATION') {
      if (isAdmin) {
        if (refId) {
          this.router.navigate(['/distribution'], { queryParams: { tab: 'dotation', uniteId: refId } });
        } else {
          this.router.navigate(['/distribution'], { queryParams: { tab: 'dotation' } });
        }
      } else {
        if (refId) {
          this.router.navigate(['/unites', refId], { queryParams: { tab: 'dotation' } });
        } else {
          this.router.navigate(['/demander-dotation']);
        }
      }
    } else if (notif.type === 'MATERIEL') {
      if (isAdmin) {
        if (refId) {
          this.router.navigate(['/distribution'], { queryParams: { uniteId: refId } });
        } else {
          this.router.navigate(['/distribution']);
        }
      } else {
        if (refId) {
          this.router.navigate(['/unites', refId], { queryParams: { tab: 'materiel' } });
        } else {
          this.router.navigate(['/demander-dotation']);
        }
      }
    } else if (notif.type === 'REFORME') {
      if (isAdmin) {
        if (refId) {
          this.router.navigate(['/reformes'], { queryParams: { uniteId: refId } });
        } else {
          this.router.navigate(['/reformes']);
        }
      } else {
        if (refId) {
          this.router.navigate(['/unites', refId], { queryParams: { tab: 'reforme' } });
        } else {
          this.router.navigate(['/reformes']);
        }
      }
    } else {
      if (refId) {
        this.router.navigate(['/unites', refId]);
      } else {
        this.router.navigate(['/catalogue']);
      }
    }
  }

  markAllAsRead(event: Event): void {
    event.stopPropagation();
    this.notificationService.markAllAsRead();
  }

  formatTime(dateStr: string): string {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMin = Math.floor(diffMs / 60000);
      const diffHrs = Math.floor(diffMin / 60);
      
      if (diffMin < 1) return 'À l\'instant';
      if (diffMin < 60) return `Il y a ${diffMin} min`;
      if (diffHrs < 24) return `Il y a ${diffHrs} h`;
      
      return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return dateStr;
    }
  }
}
