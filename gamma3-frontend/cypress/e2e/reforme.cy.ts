describe('Flux de Réforme & Déclassement de Matériel H.S.', () => {
  beforeEach(() => {
    // Nettoyer le stockage local avant chaque test
    cy.clearLocalStorage();
  });

  it('devrait exécuter le flux de réforme complet : demande client, examen commission, impact stock SRR', () => {
    // 1. Connexion en tant qu'unité cliente (DMEN)
    cy.visit('http://localhost:4200/login');
    cy.get('#matricule').type('DMEN');
    cy.get('#password').type('DMEN');
    cy.get('button[type="submit"]').click();

    // Vérifier redirection catalogue
    cy.url().should('include', '/catalogue');

    // Aller sur sa propre fiche unité
    cy.visit('http://localhost:4200/unites/1'); // DMEN est généralement le premier ID d'unité
    cy.get('.main-content-grid').should('be.visible');

    // Cliquer sur l'onglet Réforme et déclarer un matériel H.S.
    cy.contains('Réforme & Matériel H.S.').click();
    cy.contains('Déclarer Matériel H.S.').click();

    // Remplir le formulaire de déclaration H.S.
    cy.get('p-dropdown').click();
    cy.get('p-dropdown li').first().click(); // Choisir le premier article en dotation
    cy.get('#quantiteAReformer').clear().type('2');
    cy.get('#motifReforme').type('Fusil d\'assaut défectueux suite à des exercices de tir intensifs, canon fissuré.');
    
    // Soumettre le dossier de réforme
    cy.contains('Soumettre le dossier').click();
    
    // Vérifier la notification de succès et la présence dans la table
    cy.contains('Demande Soumise').should('be.visible');
    cy.contains('DR-').should('be.visible');

    // Déconnexion
    cy.get('button[title="Se déconnecter"]').click();

    // 2. Connexion en Administrateur
    cy.visit('http://localhost:4200/login');
    cy.get('#matricule').type('admin');
    cy.get('#password').type('admin');
    cy.get('button[type="submit"]').click();

    // Accéder à la console de réforme via la topbar
    cy.contains('Réforme').click();
    cy.url().should('include', '/reformes');

    // Examen en Commission de Réforme
    cy.contains('Examen Commission').first().click();
    cy.get('p-dialog').should('be.visible');

    // Sélectionner la décision de déclassement et acter
    cy.get('p-dropdown').click();
    cy.contains('Déclassement').click();
    cy.get('input[placeholder*="Membres"]').clear().type('CF Mnasri, LV Nouri, LV Elhammi');
    cy.get('textarea').clear().type('Matériel inspecté. Recommandation de déclassement physique validée vers SRR.');
    
    cy.contains('Acter la Décision').click();

    // Vérifier la notification de succès
    cy.contains('Décision Actée').should('be.visible');

    // Vérifier le bouton d'impression
    cy.contains('Imprimer PV').should('be.visible');
  });
});
