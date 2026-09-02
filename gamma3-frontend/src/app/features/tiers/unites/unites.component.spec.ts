import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UnitesComponent } from './unites.component';

describe('UnitesComponent', () => {
  let component: UnitesComponent;
  let fixture: ComponentFixture<UnitesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UnitesComponent, HttpClientTestingModule, RouterTestingModule]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(UnitesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
