import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TestBed } from '@angular/core/testing';

import { FournisseurService } from './fournisseur.service';

describe('FournisseurService', () => {
  let service: FournisseurService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule, RouterTestingModule] });
    service = TestBed.inject(FournisseurService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
