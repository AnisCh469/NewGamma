import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../core/auth/auth.service';
import {
  DocumentSigne, DocumentSigneService, TypeDocumentSigne
} from '../../core/services/document-signe.service';

/**
 * Widget réutilisable « Documents signés » : à placer sur l'écran d'un enregistrement
 * (demande, réception, réforme, commande, article…). Liste les scans de documents officiels
 * signés rattachés à cet enregistrement, permet d'en téléverser un nouveau et de les
 * consulter. Suppression réservée à l'ADMIN.
 *
 * Usage :
 *   <app-documents-signes typeDocument="BON_SORTIE" [referenceId]="dem.id"
 *                         [referenceLibelle]="dem.bonSortie?.numeroBs"></app-documents-signes>
 */
@Component({
  selector: 'app-documents-signes',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, ToastModule],
  providers: [MessageService],
  template: `
  <p-toast></p-toast>
  <div class="border border-slate-200 rounded-xl p-3 bg-slate-50/40">
    <div class="flex items-center justify-between mb-2">
      <h4 class="text-xs font-black uppercase tracking-wider text-slate-600 m-0">
        <i class="pi pi-paperclip mr-1"></i>Documents signés
        <span *ngIf="documents.length" class="ml-1 text-slate-400">({{ documents.length }})</span>
      </h4>
    </div>

    <div *ngIf="loading" class="text-center py-3 text-slate-400 text-xs">
      <i class="pi pi-spin pi-spinner"></i>
    </div>

    <ul *ngIf="!loading" class="list-none p-0 m-0 flex flex-col gap-1.5">
      <li *ngFor="let d of documents"
          class="flex items-center gap-2 bg-white border border-slate-150 rounded-lg px-2.5 py-1.5 text-xs">
        <i class="pi text-slate-400" [ngClass]="d.typeMime === 'application/pdf' ? 'pi-file-pdf' : 'pi-image'"></i>
        <button type="button" class="flex-1 text-left text-gamma-primary hover:underline truncate"
                (click)="voir(d)" [title]="d.nomFichier">{{ d.nomFichier }}</button>
        <span class="text-slate-400 whitespace-nowrap">{{ d.dateUpload | date:'dd/MM/yy HH:mm' }}</span>
        <span class="text-slate-400 whitespace-nowrap hidden sm:inline">· {{ d.uploadePar }}</span>
        <span class="text-slate-400 whitespace-nowrap">· {{ (d.taille / 1024) | number:'1.0-0' }} Ko</span>
        <button *ngIf="estAdmin" type="button" class="text-red-400 hover:text-red-600" title="Supprimer"
                (click)="supprimer(d)"><i class="pi pi-trash"></i></button>
      </li>
      <li *ngIf="!documents.length" class="text-slate-400 text-xs italic px-1 py-1">
        Aucun document signé n'a encore été joint.
      </li>
    </ul>

    <div class="mt-3 pt-2 border-t border-slate-200 flex flex-col gap-2">
      <div class="flex items-center gap-2">
        <input #fileInput type="file" accept="application/pdf,image/*" (change)="onFile($event)"
               class="text-xs flex-1
                      file:mr-2 file:py-1 file:px-3 file:rounded-md file:border-0
                      file:text-xs file:font-semibold file:bg-slate-200 file:text-slate-700
                      hover:file:bg-slate-300 file:cursor-pointer" />
      </div>
      <input type="text" [(ngModel)]="note" placeholder="Note (ex : signé par le Commandant le …) — facultatif"
             class="w-full text-xs p-2 border border-slate-200 rounded-md" />
      <p-button label="Téléverser le document signé" icon="pi pi-upload"
                styleClass="p-button-sm p-button-primary" [loading]="uploading"
                [disabled]="!selectedFile" (onClick)="televerser(fileInput)"></p-button>
    </div>
  </div>
  `
})
export class DocumentsSignesComponent implements OnInit {
  @Input({ required: true }) typeDocument!: TypeDocumentSigne;
  @Input({ required: true }) referenceId!: string | number;
  @Input() referenceLibelle?: string | null;

  private service = inject(DocumentSigneService);
  private messageService = inject(MessageService);
  private authService = inject(AuthService);

  documents: DocumentSigne[] = [];
  loading = false;
  uploading = false;
  selectedFile: File | null = null;
  note = '';

  get estAdmin(): boolean {
    return this.authService.currentUser()?.role === 'ADMIN';
  }

  ngOnInit(): void {
    this.recharger();
  }

  recharger(): void {
    if (this.referenceId === undefined || this.referenceId === null || this.referenceId === '') return;
    this.loading = true;
    this.service.lister(this.typeDocument, this.referenceId).subscribe({
      next: (list) => { this.documents = list; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files && input.files.length ? input.files[0] : null;
  }

  televerser(fileInput: HTMLInputElement): void {
    if (!this.selectedFile) return;
    this.uploading = true;
    this.service.televerser(
      this.typeDocument, this.referenceId, this.selectedFile,
      this.referenceLibelle || undefined, this.note || undefined
    ).subscribe({
      next: () => {
        this.uploading = false;
        this.selectedFile = null;
        this.note = '';
        fileInput.value = '';
        this.messageService.add({ severity: 'success', summary: 'Document joint', detail: 'Le document signé a été enregistré.' });
        this.recharger();
      },
      error: (err) => {
        this.uploading = false;
        this.messageService.add({
          severity: 'error', summary: 'Échec',
          detail: typeof err?.error === 'string' ? err.error : 'Impossible de téléverser le fichier.'
        });
      }
    });
  }

  voir(d: DocumentSigne): void {
    this.service.ouvrir(d.id);
  }

  supprimer(d: DocumentSigne): void {
    if (!confirm(`Supprimer définitivement « ${d.nomFichier} » ?`)) return;
    this.service.supprimer(d.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'warn', summary: 'Supprimé', detail: d.nomFichier });
        this.recharger();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error', summary: 'Échec',
          detail: typeof err?.error === 'string' ? err.error : 'Suppression impossible.'
        });
      }
    });
  }
}
