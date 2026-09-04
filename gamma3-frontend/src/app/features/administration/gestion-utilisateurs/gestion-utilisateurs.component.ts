import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ToolbarModule } from 'primeng/toolbar';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';

import {
  UserAdminService,
  AdminUser,
  Magasin,
  Role,
  CreateUserRequest,
  UpdateUserRequest
} from '../../../core/services/user-admin.service';
import { AuthService } from '../../../core/auth/auth.service';

/**
 * Console d'administration des comptes utilisateurs (écran Administration > Gestion des
 * Utilisateurs). Voir UserAdminController (backend) pour les 3 règles de gestion appliquées :
 * un compte UNIT_USER doit correspondre à une Unité Utilisatrice déjà créée (même code), seul
 * un ADMIN accède à cet écran, et la désactivation d'un compte est un blocage réversible.
 */
@Component({
  selector: 'app-gestion-utilisateurs',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule,
    PasswordModule, ToolbarModule, DropdownModule, TagModule],
  templateUrl: './gestion-utilisateurs.component.html',
  styleUrl: './gestion-utilisateurs.component.css'
})
export class GestionUtilisateursComponent implements OnInit {
  users: AdminUser[] = [];
  magasins: Magasin[] = [];

  searchTerm = '';

  displayDialog = false;
  isEditMode = false;
  isSaving = false;
  errorMessage = '';

  /** Erreur de chargement (ex: backend pas encore redémarré après l'ajout de ce nouvel
   * écran, ou droit ADMIN manquant) — affichée explicitement plutôt que de laisser le
   * tableau silencieusement vide, ce qui semblait suggérer "aucun compte n'existe". */
  loadErrorMessage = '';
  isLoading = false;

  displayPasswordDialog = false;
  passwordTargetUser: AdminUser | null = null;
  newPassword = '';
  isSavingPassword = false;
  passwordErrorMessage = '';

  selectedUser: {
    id?: number;
    matricule: string;
    password: string;
    fullName: string;
    role: Role | null;
    magasinId: number | null;
  } = { matricule: '', password: '', fullName: '', role: null, magasinId: null };

  roleOptions: { label: string; value: Role }[] = [
    { label: 'Administrateur (ADMIN)', value: 'ADMIN' },
    { label: 'Gestionnaire de Magasin (DA_MANAGER)', value: 'DA_MANAGER' },
    { label: "Utilisateur d'Unité (UNIT_USER)", value: 'UNIT_USER' }
  ];

  get magasinOptions(): { label: string; value: number }[] {
    return this.magasins.map(m => ({ label: `${m.code} — ${m.nom}`, value: m.id! }));
  }

  constructor(
    private userAdminService: UserAdminService,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadUsers();
    this.loadMagasins();
  }

  loadUsers(): void {
    this.isLoading = true;
    this.loadErrorMessage = '';
    this.userAdminService.getUsers().subscribe({
      next: (data) => {
        this.users = data;
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.users = [];
        if (err?.status === 404) {
          this.loadErrorMessage = "Le serveur ne reconnaît pas encore cet écran (404). "
            + "Le backend doit être redémarré pour prendre en compte cette nouvelle fonctionnalité.";
        } else if (err?.status === 403) {
          this.loadErrorMessage = "Accès refusé (403) : seul un compte ADMIN peut consulter cette page.";
        } else if (err?.status === 0) {
          this.loadErrorMessage = "Impossible de joindre le serveur backend. Vérifiez qu'il est démarré.";
        } else {
          this.loadErrorMessage = err?.error?.message || `Erreur lors du chargement des comptes (${err?.status || '?'}).`;
        }
        console.error('Erreur GET /api/v1/admin/users', err);
      }
    });
  }

  loadMagasins(): void {
    this.userAdminService.getMagasins().subscribe({
      next: (data) => {
        this.magasins = data;
      },
      error: (err) => {
        // Non bloquant pour l'écran : seul le formulaire DA_MANAGER en a besoin.
        console.error('Erreur GET /api/v1/admin/users/magasins', err);
      }
    });
  }

  clearSearch(dt: any): void {
    this.searchTerm = '';
    dt.filterGlobal('', 'contains');
  }

  isSelf(user: AdminUser): boolean {
    return this.authService.currentUser()?.matricule === user.matricule;
  }

  openNew(): void {
    this.selectedUser = { matricule: '', password: '', fullName: '', role: null, magasinId: null };
    this.isEditMode = false;
    this.errorMessage = '';
    this.displayDialog = true;
  }

  editUser(user: AdminUser): void {
    this.selectedUser = {
      id: user.id,
      matricule: user.matricule,
      password: '',
      fullName: user.fullName,
      role: user.role,
      magasinId: user.magasin?.id ?? null
    };
    this.isEditMode = true;
    this.errorMessage = '';
    this.displayDialog = true;
  }

  isFormValid(): boolean {
    if (!this.selectedUser.fullName || !this.selectedUser.role) return false;
    if (!this.isEditMode) {
      if (!this.selectedUser.matricule || this.selectedUser.password.length < 4) return false;
    }
    if (this.selectedUser.role === 'DA_MANAGER' && !this.selectedUser.magasinId) return false;
    return true;
  }

  saveUser(): void {
    this.errorMessage = '';
    this.isSaving = true;

    if (this.isEditMode && this.selectedUser.id) {
      const request: UpdateUserRequest = {
        fullName: this.selectedUser.fullName,
        role: this.selectedUser.role!,
        magasinId: this.selectedUser.role === 'DA_MANAGER' ? this.selectedUser.magasinId : null
      };
      this.userAdminService.updateUser(this.selectedUser.id, request).subscribe({
        next: () => {
          this.isSaving = false;
          this.displayDialog = false;
          this.loadUsers();
        },
        error: (err) => {
          this.isSaving = false;
          this.errorMessage = err?.error?.message || "Erreur lors de la mise à jour du compte.";
        }
      });
    } else {
      const request: CreateUserRequest = {
        matricule: this.selectedUser.matricule,
        password: this.selectedUser.password,
        fullName: this.selectedUser.fullName,
        role: this.selectedUser.role!,
        magasinId: this.selectedUser.role === 'DA_MANAGER' ? this.selectedUser.magasinId : null
      };
      this.userAdminService.createUser(request).subscribe({
        next: () => {
          this.isSaving = false;
          this.displayDialog = false;
          this.loadUsers();
        },
        error: (err) => {
          this.isSaving = false;
          this.errorMessage = err?.error?.message || "Erreur lors de la création du compte.";
        }
      });
    }
  }

  toggleEnabled(user: AdminUser): void {
    const nextEnabled = !user.enabled;
    const action = nextEnabled ? 'débloquer' : 'bloquer';
    if (!confirm(`Voulez-vous vraiment ${action} le compte ${user.matricule} ?`)) return;

    this.userAdminService.setEnabled(user.id!, nextEnabled).subscribe({
      next: () => this.loadUsers(),
      error: (err) => alert(err?.error?.message || `Erreur lors de l'opération.`)
    });
  }

  openResetPassword(user: AdminUser): void {
    this.passwordTargetUser = user;
    this.newPassword = '';
    this.passwordErrorMessage = '';
    this.displayPasswordDialog = true;
  }

  saveNewPassword(): void {
    if (!this.passwordTargetUser?.id) return;
    if (this.newPassword.length < 4) {
      this.passwordErrorMessage = 'Le mot de passe doit contenir au moins 4 caractères.';
      return;
    }
    this.isSavingPassword = true;
    this.passwordErrorMessage = '';
    this.userAdminService.resetPassword(this.passwordTargetUser.id, this.newPassword).subscribe({
      next: () => {
        this.isSavingPassword = false;
        this.displayPasswordDialog = false;
      },
      error: (err) => {
        this.isSavingPassword = false;
        this.passwordErrorMessage = err?.error?.message || "Erreur lors de la réinitialisation.";
      }
    });
  }

  getRoleSeverity(role: string): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | 'contrast' | undefined {
    switch (role) {
      case 'ADMIN': return 'danger';
      case 'DA_MANAGER': return 'warning';
      case 'UNIT_USER': return 'info';
      default: return 'secondary';
    }
  }
}
