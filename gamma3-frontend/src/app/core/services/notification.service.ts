import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subscription, interval } from 'rxjs';
import { AuthService } from '../auth/auth.service';

import { environment } from '../../../environments/environment';
export interface Notification {
  id: number;
  title: string;
  message: string;
  type: 'DOTATION' | 'MATERIEL' | 'REFORME' | 'SYSTEM';
  uniteCode?: string;
  referenceId?: string;
  read: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private apiUrl = `${environment.apiUrl}/api/v1/notifications`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  public notifications = signal<Notification[]>([]);
  public unreadCount = signal<number>(0);

  private pollingSub?: Subscription;

  constructor() {
    if (this.authService.isAuthenticated()) {
      this.startPolling();
    }
  }

  fetchNotifications(): void {
    if (!this.authService.isAuthenticated()) return;
    
    this.http.get<Notification[]>(this.apiUrl).subscribe({
      next: (notifs) => {
        this.notifications.set(notifs);
        const unread = notifs.filter(n => !n.read).length;
        this.unreadCount.set(unread);
      },
      error: (err) => console.error('Error fetching notifications', err)
    });
  }

  fetchUnreadCount(): void {
    if (!this.authService.isAuthenticated()) return;

    this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`).subscribe({
      next: (res) => {
        this.unreadCount.set(res.count);
      },
      error: (err) => console.error('Error fetching unread count', err)
    });
  }

  markAsRead(id: number): void {
    this.http.post<void>(`${this.apiUrl}/${id}/read`, {}).subscribe({
      next: () => {
        this.notifications.update(notifs => 
          notifs.map(n => n.id === id ? { ...n, read: true } : n)
        );
        this.updateUnreadCountLocally();
      },
      error: (err) => console.error('Error marking notification as read', err)
    });
  }

  markAllAsRead(): void {
    this.http.post<void>(`${this.apiUrl}/read-all`, {}).subscribe({
      next: () => {
        this.notifications.update(notifs => 
          notifs.map(n => ({ ...n, read: true }))
        );
        this.unreadCount.set(0);
      },
      error: (err) => console.error('Error marking all notifications as read', err)
    });
  }

  private updateUnreadCountLocally(): void {
    const unread = this.notifications().filter(n => !n.read).length;
    this.unreadCount.set(unread);
  }

  startPolling(): void {
    if (this.pollingSub) return;
    
    this.fetchNotifications();

    this.pollingSub = interval(10000).subscribe(() => {
      this.fetchNotifications();
    });
  }

  stopPolling(): void {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
      this.pollingSub = undefined;
    }
  }
}
