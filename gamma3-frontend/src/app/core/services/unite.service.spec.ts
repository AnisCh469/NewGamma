import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TestBed } from '@angular/core/testing';

import { UniteService } from './unite.service';

describe('UniteService', () => {
  let service: UniteService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule, RouterTestingModule] });
    service = TestBed.inject(UniteService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
