import { Component, inject, effect, HostListener } from '@angular/core';
import { RouterOutlet, RouterModule, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth/auth.service';
import { NotificationService } from './core/services/notification.service';
import { UniteService } from './core/services/unite.service';

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
  router = inject(Router);

  isDrawerOpen = false;
  unitId: number | null = null;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.isDrawerOpen) {
      this.closeDrawer();
    }
  }

  constructor() {
    // Reactively start or stop polling based on the user's logged-in state
    effect(() => {
      const user = this.authService.currentUser();
      if (user) {
        this.notificationService.startPolling();
        
        // If client, retrieve their unit ID to enable direct links
        if (user.role === 'UNIT_USER') {
          this.uniteService.getUniteByCode(user.matricule).subscribe({
            next: (u) => {
              if (u && u.id) {
                this.unitId = u.id;
              }
            },
            error: (err) => console.error('Error loading client unit ID', err)
          });
        }
      } else {
        this.notificationService.stopPolling();
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
    const isAdmin = role === 'ADMIN' || role === 'DA_MANAGER';
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
