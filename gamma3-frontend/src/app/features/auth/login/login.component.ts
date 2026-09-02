import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  matricule = '';
  password = '';
  code2fa = '';
  show2faForm = false;
  errorMessage = '';
  isLoading = false;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit() {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/catalogue']);
    }
  }

  onLogin() {
    if (!this.matricule || !this.password) {
      this.errorMessage = 'Veuillez remplir tous les champs.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login({ matricule: this.matricule, password: this.password }).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.requires2fa) {
          this.show2faForm = true;
        } else {
          this.router.navigate(['/catalogue']);
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Identifiants incorrects ou problème de connexion.';
        console.error('Login error', err);
      }
    });
  }

  onVerify2Fa() {
    if (!this.code2fa) {
      this.errorMessage = 'Veuillez saisir votre code de sécurité à 6 chiffres.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.verify2Fa(this.matricule, this.code2fa).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/catalogue']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Code de sécurité incorrect ou expiré.';
        console.error('2FA verification error', err);
      }
    });
  }
}
