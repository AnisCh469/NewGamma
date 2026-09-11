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
  UniteUtilisatriceRef,
  Role,
  FonctionUnite,
  CreateUserRequest,
  UpdateUserRequest
} from '../../../core/services/user-admin.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ParametreService } from '../../../core/services/parametre.service';

/**
 * Console d'administration des comptes utilisateurs (écran Administration > Gestion des
 * Utilisateurs). Voir UserAdminController (backend) pour les règles de gestion appliquées :
 * seul un ADMIN accède à cet écran, la désactivation d'un compte est un blocage réversible,
 * et depuis le 2026-09-04 chaque Unité Utilisatrice dispose de DEUX comptes UNIT_USER
 * distincts — un Gestionnaire (soumet les demandes) et un Commandant (les valide avant envoi
 * à GAMMA3) — chacun explicitement rattaché à son unité via un sélecteur dédié (FonctionUnite).
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
  unites: UniteUtilisatriceRef[] = [];

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
    uniteId: number | null;
    fonctionUnite: FonctionUnite | null;
  } = { matricule: '', password: '', fullName: '', role: null, magasinId: null, uniteId: null, fonctionUnite: null };

  roleOptions: { label: string; value: Role }[] = [
    { label: 'Administrateur Système (SUPER_ADMIN)', value: 'SUPER_ADMIN' },
    { label: 'Administrateur Logistique — SGS (ADMIN)', value: 'ADMIN' },
    { label: 'Gestionnaire de Magasin (DA_MANAGER)', value: 'DA_MANAGER' },
    { label: "Utilisateur d'Unité (UNIT_USER)", value: 'UNIT_USER' }
  ];

  fonctionOptions: { label: string; value: FonctionUnite }[] = [
    { label: 'Gestionnaire (soumet les demandes)', value: 'GESTIONNAIRE' },
    { label: 'Commandant (valide les demandes)', value: 'COMMANDANT' }
  ];

  get magasinOptions(): { label: string; value: number }[] {
    return this.magasins.map(m => ({ label: `${m.code} — ${m.nom}`, value: m.id! }));
  }

  get uniteOptions(): { label: string; value: number }[] {
    return this.unites.map(u => ({ label: `${u.code} — ${u.nom}`, value: u.id! }));
  }

  // Sécurité de session (délai d'inactivité, configurable — demande utilisateur 2026-09-11)
  inactiviteMinutes: number | null = null;
  inactiviteMinutesInput: number = 5;
  isSavingInactivite = false;
  inactiviteMessage = '';

  constructor(
    private userAdminService: UserAdminService,
    public authService: AuthService,
    private parametreService: ParametreService
  ) {}

  ngOnInit(): void {
    this.loadUsers();
    this.loadMagasins();
    this.loadUnites();
    this.loadInactivite();
  }

  loadInactivite(): void {
    this.parametreService.getInactiviteMinutes().subscribe({
      next: (r) => { this.inactiviteMinutes = r.inactiviteMinutes; this.inactiviteMinutesInput = r.inactiviteMinutes; },
      error: () => {}
    });
  }

  enregistrerInactivite(): void {
    if (!this.inactiviteMinutesInput || this.inactiviteMinutesInput < 1 || this.inactiviteMinutesInput > 480) {
      this.inactiviteMessage = 'La durée doit être comprise entre 1 et 480 minutes.';
      return;
    }
    this.isSavingInactivite = true;
    this.inactiviteMessage = '';
    this.parametreService.setInactiviteMinutes(this.inactiviteMinutesInput).subscribe({
      next: (r) => {
        this.isSavingInactivite = false;
        this.inactiviteMinutes = r.inactiviteMinutes;
        this.inactiviteMessage = `Délai enregistré : ${r.inactiviteMinutes} min. Les sessions déjà ouvertes appliqueront ce nouveau délai à leur prochaine activité.`;
      },
      error: (err) => {
        this.isSavingInactivite = false;
        this.inactiviteMessage = err?.error || "Erreur lors de l'enregistrement.";
      }
    });
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
      next: (data) => { this.magasins = data; },
      error: (err) => console.error('Erreur GET /api/v1/admin/users/magasins', err)
    });
  }

  loadUnites(): void {
    this.userAdminService.getUnites().subscribe({
      next: (data) => { this.unites = data; },
      error: (err) => console.error('Erreur GET /api/v1/admin/users/unites', err)
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
    this.selectedUser = { matricule: '', password: '', fullName: '', role: null, magasinId: null, uniteId: null, fonctionUnite: null };
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
      magasinId: user.magasin?.id ?? null,
      uniteId: user.unite?.id ?? null,
      fonctionUnite: user.fonctionUnite ?? null
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
    if (this.selectedUser.role === 'UNIT_USER' && (!this.selectedUser.uniteId || !this.selectedUser.fonctionUnite)) return false;
    return true;
  }

  saveUser(): void {
    this.errorMessage = '';
    this.isSaving = true;

    if (this.isEditMode && this.selectedUser.id) {
      const request: UpdateUserRequest = {
        fullName: this.selectedUser.fullName,
        role: this.selectedUser.role!,
        magasinId: this.selectedUser.role === 'DA_MANAGER' ? this.selectedUser.magasinId : null,
        uniteId: this.selectedUser.role === 'UNIT_USER' ? this.selectedUser.uniteId : null,
        fonctionUnite: this.selectedUser.role === 'UNIT_USER' ? this.selectedUser.fonctionUnite : null
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
        magasinId: this.selectedUser.role === 'DA_MANAGER' ? this.selectedUser.magasinId : null,
        uniteId: this.selectedUser.role === 'UNIT_USER' ? this.selectedUser.uniteId : null,
        fonctionUnite: this.selectedUser.role === 'UNIT_USER' ? this.selectedUser.fonctionUnite : null
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

  /**
   * Code couleur des rôles (2026-09-11, retour utilisateur) : SUPER_ADMIN (pouvoirs étendus —
   * comptes, sécurité, sauvegardes, migrations) affiché en "danger" (rouge) pour signaler un
   * compte à manier avec prudence ; ADMIN (usage logistique quotidien, SGS = Service de
   * Gestion des Stocks) affiché en "success" (vert), un compte d'usage courant sans risque
   * particulier. Cohérent avec le badge de profil dans l'en-tête (app.component.html).
   */
  getRoleSeverity(role: string): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | 'contrast' | undefined {
    switch (role) {
      case 'SUPER_ADMIN': return 'danger';
      case 'ADMIN': return 'success';
      case 'DA_MANAGER': return 'warning';
      case 'UNIT_USER': return 'info';
      default: return 'secondary';
    }
  }
}
